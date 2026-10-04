pluginManagement {
    repositories {
        // Official repositories first: a 5xx from a mirror aborts resolution instead of
        // falling through, so mirrors must never be the first place Gradle looks.
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
    }
}

rootProject.name = "DSHA-Next"

include(":app")
