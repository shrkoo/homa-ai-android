pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.myket.ir/repos") }  // Myket SDK
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "HomaAI"
include(":app")
