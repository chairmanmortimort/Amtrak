pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // The Light keyboard library (com.github.lightphone:light-keyboard) is a
        // transitive dependency of :sdk:ui and resolves from JitPack.
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
    // Reuse the SDK's own version catalog so this shell never drifts from
    // whatever the pinned submodule expects.
    versionCatalogs {
        create("libs") {
            from(files("light-sdk/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "amtrak"

// The `light.sdk` Gradle plugin lives in the submodule and is contributed as
// an included build (matches the SDK monorepo's own includeBuild("plugin")).
includeBuild("light-sdk/plugin")

// SDK library projects, sourced from the submodule. :sdk:client transitively
// needs :sdk:ui, :sdk:shared and :lint-rules.
include(":lint-rules")
project(":lint-rules").projectDir = file("light-sdk/lint-rules")
include(":sdk")
project(":sdk").projectDir = file("light-sdk/sdk")
include(":sdk:shared")
project(":sdk:shared").projectDir = file("light-sdk/sdk/shared")
include(":sdk:ui")
project(":sdk:ui").projectDir = file("light-sdk/sdk/ui")
include(":sdk:client")
project(":sdk:client").projectDir = file("light-sdk/sdk/client")

// The tool itself -- the only dev-owned module, and the only thing Light's
// build/review pipeline extracts (tool/build.gradle.kts, tool/lighttool.toml,
// tool/src/main/**).
include(":tool")
