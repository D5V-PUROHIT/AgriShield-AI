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

rootProject.name = "AgriShield AI"
include(":app")
project(":app").projectDir = file("agrishield_app/android/app")
