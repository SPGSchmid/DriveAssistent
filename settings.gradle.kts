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
        // Spotify App Remote SDK
        maven { url = uri("https://spotify.github.io/android-sdk/") }
        // Microsoft Identity (MSAL) – falls nicht über MavenCentral
        maven { url = uri("https://pkgs.dev.azure.com/MicrosoftDevDiv/927922f3-bc75-49e2-9b6e-d28ba4df7fd9/_packaging/azure-client-sdk-android/maven/v1") }
    }
}

rootProject.name = "DriveAssistant"
include(":app")
