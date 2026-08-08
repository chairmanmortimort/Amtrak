# Amtrak Transit Tool for Light Phone III

A transit app for the Light Phone III built with the Light Phone SDK, displaying real-time train status, stations, and schedules from the Amtraker API.

## Files in this package

- `amtrak-tool.tar.gz` — The full project source

## How to build on your machine

### Prerequisites

- Android Studio installed
- JDK 17 or 21
- A GitHub personal access token (PAT) with `read:packages` scope — see below

### Option A: Use your own GitHub token (recommended)

1. Unpack the tarball:
   ```bash
   tar -xzf amtrak-tool.tar.gz
   cd amtrak-tool
   ```

2. Create `local.properties` with your GitHub credentials:
   ```bash
   echo "gpr.user=YOUR_GITHUB_USERNAME" >> local.properties
   echo "gpr.key=ghp_YOUR_PERSONAL_ACCESS_TOKEN" >> local.properties
   ```

3. Build the debug APK:
   ```bash
   ./gradlew :tool:assembleDebug
   ```

4. The APK will be at: `tool/build/outputs/apk/debug/tool-debug.apk`

### Option B: Build without GitHub token (no keyboard support)

If you don't need the keyboard module, the repo is already set up to skip it. You can try building without setting up `local.properties`:

```bash
tar -xzf amtrak-tool.tar.gz
cd amtrak-tool
./gradlew :tool:assembleDebug
```

### Getting a GitHub PAT

1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Give it a name like "Light SDK Build"
4. Check the `read:packages` scope
5. Generate the token and copy it
6. Paste it into `local.properties` as shown above

## What this app does

- **Trains tab**: Shows all active Amtrak trains with route, origin/destination, current stop, and delay status
- **Stations tab**: Shows all Amtrak stations; tap to see upcoming arrivals
- **Search tab**: Search by train number, route name, or station name
- **Train detail**: Tap any train to see heading, speed, last updated time, and stop list
- **Station detail**: Tap any station to see upcoming trains with arrival/departure times

## API

Uses the free community API at `https://api.amtraker.com/v1` — no API key required.

## About

Built with the Light Phone III SDK. See https://github.com/lightphone/light-sdk for SDK details.
