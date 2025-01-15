// tag::simple-setting-repositories[]
pluginManagement {  // <1>
    repositories {
        gradlePluginPortal()
    }
}
// end::simple-setting-repositories[]

// tag::simple-setting-plugins[]
plugins {   // <2>
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
// end::simple-setting-plugins[]

// tag::simple-setting-name[]
rootProject.name = "simple-project"     // <3>
// end::simple-setting-name[]

// tag::simple-setting-dep[]
dependencyResolutionManagement {    // <4>
    repositories {
        mavenCentral()
    }
}
// end::simple-setting-dep[]

// tag::simple-setting-sub[]
include("sub-project-a")     // <5>
include("sub-project-b")
include("sub-project-c")
// end::simple-setting-sub[]
