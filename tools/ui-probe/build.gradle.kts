plugins { alias(libs.plugins.android.application) }

android {
    namespace = "io.booup.roadmate.uiprobe"
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    defaultConfig {
        applicationId = "io.booup.roadmate.uiprobe"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "phase8-ui-observer"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// Framework APIs only. No library, SDK, vehicle or application dependency.
