# Submission: Amtrak

Light Tool Library metadata - paste into the **Add Tool** form.

| Field | Value |
|---|---|
| **Title** | Amtrak |
| **GitHub Repo** | https://github.com/<you>/amtrak-light |
| **Description** | Live Amtrak train status for the Light Phone III. Enter a train number to see its route, stops and estimated arrivals and departures. |
|| **Tags** | Navigation and Travel *(provisional)* |

## Build artifact (v1.2.0 / versionCode 3)
- **Release APK (minified + shrink + arm64-v8a, ~4.8 MB):** `Amtrak-v1.2.0-release.apk`
  - Path: `/Users/mattdelaus/Desktop/LP34REEL/Amtrak/submission/Amtrak-v1.2.0-release.apk`
  - versionCode = 3, versionName = 1.2.0 (per `tool/lighttool.toml`)
- **v1.2 changes in this build:** uniform square route-progress markers (Past `◼` / Current `◼` full-brightness / Future `□`), connecting line through all stops anchored to the RIGHT of each stop row, `lightClickable` haptics on every tap target, delay computed from actual-vs-scheduled timestamps (`arr-schArr`, `dep-schDep`) instead of empty comment fields, `trainTimely` typed as String (live API returns `""`, not a boolean), and Light UI typography (no monospace on stop names/times; marker-only highlight on the Current stop — no full-row background).
- **Debug APK (unminified, ~57.5 MB, for local testing):** `Amtrak-v1.2.0-debug.apk` — same source, same versionCode 3 / 1.2.0.
- **Verification:** both APKs rebuilt from current sources; release dex contains the new square markers and none of the old round ones; `aapt2 dump` on the release APK confirms versionCode=3 / versionName=1.2.0.

## Checklist
- [ ] Public repo created
- [ ] >=1 screenshot added (required for approval)
- [ ] Description <= 255 chars
- [x] versionCode bumped (3)
- [ ] Built with the latest light-sdk
