plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Opt-in acceptance APK uses the exact Production sources with an isolated UID/data/Keystore.
// It never builds a release artifact or replaces the user's normal package/store.
val isolatedAcceptance=providers.gradleProperty("phase86IsolatedAcceptance").isPresent
if(isolatedAcceptance) layout.buildDirectory.set(layout.projectDirectory.dir("build/phase86-acceptance"))
val internalCollabs = providers.gradleProperty("roadmateInternalCollabs").isPresent
require(!(isolatedAcceptance && internalCollabs)) { "Acceptance and collab builds must use separate packages and stores" }
if (internalCollabs) {
    val preview = rootProject.layout.projectDirectory.dir("development/collab-preview")
    layout.buildDirectory.set(preview.dir("build"))
    tasks.register<Copy>("packageInternalCollab") {
        dependsOn("assembleDemoDebug")
        from(layout.buildDirectory.file("outputs/apk/demo/debug/app-demo-debug.apk"))
        into(preview.dir("apk"))
        rename { "roadmate-collab-debug.apk" }
    }
}

android {
    namespace = "io.booup.roadmate"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = if(isolatedAcceptance) "io.booup.roadmate.acceptance" else "io.booup.roadmate"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.7.0-phase7"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = if (internalCollabs) ".demo.collab" else ".demo"
            versionNameSuffix = "-demo"
        }
        // 'production' is a flavor; 'release' remains the standard Android build type.
        create("production") {
            dimension = "environment"
            // Framework Instrumentation only; runner is packaged in the separate test APK.
            testInstrumentationRunner = "io.booup.roadmate.integration.Phase86Instrumentation"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("test").java.srcDir("../core/contracts/src/testSupport/kotlin")
    sourceSets.getByName("debug").res.srcDir(rootProject.file("development/local-fonts/res"))
    if (internalCollabs) {
        val preview = rootProject.file("development/collab-preview")
        sourceSets.maybeCreate("demoDebug").apply {
            assets.srcDir(preview.resolve("assets"))
            res.srcDir(preview.resolve("res"))
        }
    }
    // Platform-provided AAOS API, not packaged SDK classes or an invented Maven artifact.
    useLibrary("android.car")
}

// Prevent a release artifact that could accidentally contain demo fixtures.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (isolatedAcceptance || variant.productFlavors.any { it.second == "demo" }) variant.enable = false
    }
}

dependencies {
    implementation(project(":core:contracts"))
    add("productionImplementation", "ai.pleos.playground:Vehicle:2.3.2")
    // The SDK exposes ErrorCode from its runtime-only vehicle-data POM dependency.
    // Promote that already-present, verified 1.1.11 dependency to the compile/test ABI.
    add("productionImplementation", "ai.pleos.playground:vehicle-data:1.1.11")
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
