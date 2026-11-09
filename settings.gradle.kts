pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroup("ai.pleos.playground") }
        }
    }
}

rootProject.name = "AI-Roadmate"
include(":core:model", ":core:contracts")
// JVM contracts can be compiled without Android SDK or Android Gradle Plugin.
if (providers.gradleProperty("coreOnly").isPresent) {
    rootProject.buildFileName = "build-core.gradle.kts"
} else {
    include(":app")
    // Optional read-only AVD test tool; never a dependency of the product app.
    if (providers.gradleProperty("phase8UiProbe").isPresent) {
        include(":ui-probe")
        project(":ui-probe").projectDir = file("tools/ui-probe")
    }
}
