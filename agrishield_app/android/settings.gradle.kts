pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.android.application") version "8.5.2" apply false
}

rootProject.name = "agrishield_app"
include(":app")
