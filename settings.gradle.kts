rootProject.name = "cloche"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("idea")
project(":idea").name = "cloche-idea"
