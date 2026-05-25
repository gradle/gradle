// tag::settings-breakdown[]
plugins {                                                                   // <1>
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"  // <2>
}

rootProject.name = "authoring-tutorial"                                     // <3>

include("app")                                                              // <4>
include("lib")

includeBuild("gradle/license-plugin")                                       // <5>
// end::settings-breakdown[]
