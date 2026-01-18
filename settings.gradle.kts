enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "cloche"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("models")
project(":models").name = "cloche-models"
include("idea")
project(":idea").name = "cloche-idea"
