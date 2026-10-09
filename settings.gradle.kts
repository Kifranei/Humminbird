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
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        // HONOR Audio Kit publishes the optional HD audio playback SDK here.
        maven { url = uri("https://developer.honor.com/repo") }
    }
}

rootProject.name = "Humminbird"
include(":app")
include(":ffmpeg-decoder")
include(":lyrico-audiotag")
include(":hidden-api")
