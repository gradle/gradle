dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// tag::do-this[]
include("app") // <1>
include("util")
include("util-commons")
include("util-guava")
// end::do-this[]

rootProject.name = "modularize-your-build"
