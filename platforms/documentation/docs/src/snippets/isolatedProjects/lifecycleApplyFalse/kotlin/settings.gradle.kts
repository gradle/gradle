// tag::settings-file[]
pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Resolves `my.convention` against the included plugin build and
    // exports its classpath to all projects, without applying it here.
    id("my.convention") apply false
}

include("sub1")
include("sub2")

gradle.lifecycle.beforeProject {
    apply(plugin = "my.convention")
}
// end::settings-file[]
