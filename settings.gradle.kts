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

rootProject.name = "Zhiyu"

include(":app")
include(":core:domain")
include(":core:storage")
include(":core:network")
include(":core:data")
include(":feature:auth")
include(":feature:dashboard")
include(":feature:widget")
include(":feature:settings")
