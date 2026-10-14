// Thin root. The SDK library projects and the tool are configured in
// settings.gradle.kts; this file only provides the plugin classpath and the
// `ext` build knobs the SDK build scripts read via `rootProject.ext[...]`.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

group = "com.thelightphone"

ext["compileSdk"] = 36
ext["minSdk"] = 34
ext["targetSdk"] = 36
ext["jvmTarget"] = "17"
ext["lintVersion"] = "31.12.3"
