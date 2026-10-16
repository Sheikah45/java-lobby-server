pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id("io.quarkus") version "4.0.0.Beta1"
    }
}
rootProject.name = "java-lobby-server"

include("player")
include("game")
include("matchmaker")