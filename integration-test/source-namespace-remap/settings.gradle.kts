pluginManagement {
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.msrandom.net/repository/cloche/")
    }
}

rootProject.name = "cloche-source-namespace-remap-integration"

include(
    "fixture-default",
    "fixture-srg",
    "fixture-child",
    "fixture-ordinary-compile",
    "fixture-ordinary-runtime",
    "fixture-policy-helper",
    "fixture-remap-root",
)