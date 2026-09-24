// tag::add-build[]
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "authoring-tutorial"

include("app")
include("subproject")

includeBuild("gradle/license-plugin") // Add the new build
// end::add-build[]
