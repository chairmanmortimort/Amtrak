#!/usr/bin/env python3
"""
Pre-process GTFS data into a compact JSON format for the CDTA app on Light Phone III.
Extracts stops, trip stop sequences with scheduled times, routes, and trip metadata.
The stop_times data enables 'where should the bus be now' estimation based on schedule.

Optimization: Only keep weekday service trips (most useful for users), and dedupe
identical stop sequences to reduce data size while preserving schedule information.
"""

import csv
import io
import json
import os
import zipfile
import gzip
import sys
from collections import defaultdict

GTFS_ZIP = "/home/mortimort/google_transit.zip"
OUTPUT = "/home/mortimort/light-sdk/examples/cdta/src/main/assets/cdta_gtfs.json"

def read_csv(zip_ref, filename):
    """Read a CSV file from the GTFS zip."""
    with zip_ref.open(filename) as f:
        reader = csv.DictReader(io.TextIOWrapper(f, 'utf-8'))
        return list(reader)

def parse_time(seconds):
    """Convert HH:MM:SS to seconds past midnight. Handles >24h values."""
    parts = seconds.split(':')
    return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])

def main():
    print(f"Processing {GTFS_ZIP}...")

    with zipfile.ZipFile(GTFS_ZIP, 'r') as z:
        stops_data = read_csv(z, 'stops.txt')
        routes_data = read_csv(z, 'routes.txt')
        trips_data = read_csv(z, 'trips.txt')
        stop_times_data = read_csv(z, 'stop_times.txt')
        calendar_data = read_csv(z, 'calendar.txt') if 'calendar.txt' in z.namelist() else []

    # Build service ID -> days set
    service_days = {}
    for row in calendar_data:
        sid = row['service_id']
        days = set()
        for col in ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']:
            if row.get(col, '0') == '1':
                days.add(col)
        service_days[sid] = days

    # Parse stops: stop_id -> [name, lat, lon, stop_id]
    stops = {}
    for row in stops_data:
        sid = row['stop_id']
        stops[sid] = [
            row['stop_name'],
            float(row['stop_lat']),
            float(row['stop_lon']),
            sid
        ]

    # Parse routes
    routes = {}
    for row in routes_data:
        rid = row['route_id']
        routes[rid] = {
            'short': row.get('route_short_name', ''),
            'long': row.get('route_long_name', ''),
            'desc': row.get('route_desc', ''),
            'color': row.get('route_color', ''),
            'text_color': row.get('route_text_color', ''),
        }

    # Parse trips: keep weekday trips (service runs Monday-Friday)
    # Also map route_id -> route_short_name
    route_id_to_short = {}
    for rid, rdata in routes.items():
        route_id_to_short[rid] = rdata['short']

    trip_meta = {}
    weekday_trips = set()

    for row in trips_data:
        tid = row['trip_id']
        sid = row['service_id']
        rid = row.get('route_id', '')
        short_name = route_id_to_short.get(rid, '')

        # Track all trips
        trip_meta[tid] = {
            'headsign': row.get('trip_headsign', ''),
            'direction': int(row.get('direction_id', 0)),
        }

        # Keep weekday service trips
        days = service_days.get(sid, set())
        if 'monday' in days and 'friday' in days:
            weekday_trips.add(tid)

    print(f"  Total trips: {len(trips_data)}, weekday trips: {len(weekday_trips)}")

    # Parse stop_times: trip_id -> list of [stop_id, arrival_seconds, departure_seconds]
    trip_stop_times = defaultdict(list)
    for row in stop_times_data:
        tid = row['trip_id']
        # Only process weekday trips
        if tid not in weekday_trips:
            continue
        sid = row['stop_id']
        arrival = row.get('arrival_time', '0:00:00')
        departure = row.get('departure_time', arrival)
        seq = int(row.get('stop_sequence', 0))

        arrival_sec = parse_time(arrival) if arrival else 0
        dep_sec = parse_time(departure) if departure else arrival_sec

        trip_stop_times[tid].append({
            'stop_id': sid,
            'arrival_time': arrival_sec,
            'departure_time': dep_sec,
            'stop_sequence': seq,
        })

    # Sort each trip's stops by sequence and create compact arrays
    trip_stops = {}
    trip_times = {}
    for tid, stops_list in trip_stop_times.items():
        sorted_stops = sorted(stops_list, key=lambda x: x['stop_sequence'])
        trip_stops[tid] = [s['stop_id'] for s in sorted_stops]
        # Store arrival/departure times as compact arrays of [arrival_sec, departure_sec]
        trip_times[tid] = [[s['arrival_time'], s['departure_time']] for s in sorted_stops]

    # Build route_trips: route_short_name -> list of trip_ids (weekday only)
    route_trips = defaultdict(list)
    for tid in trip_stop_times:
        rid = None
        # Find the route for this trip
        for row in trips_data:
            if row['trip_id'] == tid:
                rid = row.get('route_id', '')
                break
        if not rid:
            continue
        short_name = route_id_to_short.get(rid, '')
        if short_name:
            route_trips[short_name].append(tid)

    # Build output
    output = {
        'stops': stops,
        'trip_stops': trip_stops,
        'trip_times': trip_times,
        'trip_meta': trip_meta,
        'route_trips': dict(route_trips),
        'routes': routes,
    }

    total_trips = sum(len(v) for v in trip_stops.values())
    total_stop_times = sum(len(v) for v in trip_times.values())
    print(f"Output stats: {len(stops)} stops, {len(routes)} routes, {len(trip_stops)} trips, {total_stop_times} stop_times")

    with open(OUTPUT, 'w') as f:
        json.dump(output, f)

    gz_output = OUTPUT + '.gz'
    with gzip.open(gz_output, 'wb') as f_gz:
        with open(OUTPUT, 'rb') as f_in:
            f_gz.write(f_in.read())
    gz_size = os.path.getsize(gz_output)
    print(f"Gzip size: {gz_size} bytes ({gz_size/1024:.0f} KB)")

    # Remove the raw uncompressed JSON — Android auto-decompresses .gz assets
    # and having both .json and .json.gz in the assets folder causes a
    # "Duplicate resources" build error.
    os.remove(OUTPUT)
    print("Removed raw JSON (keeping only .gz for Android asset auto-decompression)")
    print(f"Done!")

if __name__ == '__main__':
    main()