# Amtrak

An Amtrak train tracker for the Light Phone III. Look up trains and stations and follow a train's live status along its route.

A community tool for the [Light Phone III](https://www.thelightphone.com/), built on the official
[light-sdk](https://github.com/lightphone/light-sdk) with Kotlin, Jetpack Compose, `LightScreen`
and `LightViewModel`, themed with `sdk:ui`.

## Layout

```
amtrak/
├── light-sdk/            # git submodule -> upstream Light SDK (pinned commit)
├── tool/                 # the ONLY dev-owned module
│   ├── lighttool.toml    # tool id, label, version, declared permissions
│   ├── build.gradle.kts  # dependencies (project(":sdk:client") + light.sdk plugin)
│   └── src/main/**       # tool source
├── settings.gradle.kts   # grafts the submodule's SDK projects into this build
├── build.gradle.kts      # thin root: plugin classpath + ext build knobs
├── gradle.properties
└── gradlew, gradle/       # wrapper
```

Light's containerized builder clones this repo and extracts **only**
`tool/build.gradle.kts`, `tool/lighttool.toml`, and `tool/src/main/**`, then compiles them against
its own pinned, sandboxed copy of the SDK. It generates `AndroidManifest.xml` from `lighttool.toml`,
so this repo intentionally contains no hand-written manifest and never sets `applicationId`,
`versionCode`, `versionName`, or `namespace`.

## Building locally

Requires JDK 17 and an Android SDK. Then:

```
git submodule update --init --recursive
./gradlew :tool:assembleDebug
# -> tool/build/outputs/apk/debug/tool-debug.apk
```

The APK is signed with the shared Light dev keystore (from the submodule) for local sideloading.
Light's build service re-signs with its own key.

## Privacy

The Amtrak tool contacts the public amtraker.com API to fetch live train data. Your requests and IP address reach that service. No account is required, no analytics or tracking is used, and no data is stored on any server we operate.

See PRIVACY.md for the network endpoints it contacts.

## Content & licensing

- Live train data: amtraker.com, a third-party community API. It is not affiliated
  with or endorsed by Amtrak. See the longevity note in PRIVACY.md.

## License

MIT, the same as upstream light-sdk. See [LICENSE](LICENSE).

The Light Phone, LightOS, and Light Phone III are names of The Light Phone; this project is not
affiliated with or endorsed by The Light Phone.
