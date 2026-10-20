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
    }
}

rootProject.name = "FBRClients"
include(":app")
// Logic used by both the phone app and the PC app (see shared/README.md).
include(":shared")
// The Windows PC app (see desktop/README.md).
include(":desktop")
