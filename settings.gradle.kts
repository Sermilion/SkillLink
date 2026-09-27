pluginManagement {
    includeBuild("build-logic") {
        name = "build-logic"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "skilllink"

include("domain", "application", "infrastructure", "desktop", "cli", "app")
