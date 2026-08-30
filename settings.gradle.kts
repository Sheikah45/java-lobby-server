pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id("io.quarkus") version "3.39.1"
    }
}
rootProject.name = "java-lobby-server"

include("server")