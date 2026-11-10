pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "GymTracker"

// Módulos JVM puros (sin Android): se compilan y prueban en cualquier máquina con JDK.
include(":domain")
include(":crypto")
// Módulos Android.
include(":data")
include(":sync")
include(":app")
