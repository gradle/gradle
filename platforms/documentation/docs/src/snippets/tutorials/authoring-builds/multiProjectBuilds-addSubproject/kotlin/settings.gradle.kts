// tag::add-subproject[]
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "authoring-tutorial"

include("app")
include("lib") // Add lib to the build
// end::add-subproject[]
