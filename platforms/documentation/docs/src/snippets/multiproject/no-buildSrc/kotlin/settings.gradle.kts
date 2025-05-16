plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "multiproject-buildSrc"
include("api", "shared", "services")
