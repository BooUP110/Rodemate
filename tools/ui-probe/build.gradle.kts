plugins { alias(libs.plugins.android.application) }

val naviProbe = providers.gradleProperty("roadmateNaviProbe").isPresent

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
    if (naviProbe) {
        sourceSets.named("main") {
            java.srcDir("src/navi/java")
            manifest.srcFile("src/navi/AndroidManifest.xml")
        }
    }
}

if (naviProbe) {
    dependencies { implementation("ai.pleos.playground:NaviHelper:3.0.5") }
}
