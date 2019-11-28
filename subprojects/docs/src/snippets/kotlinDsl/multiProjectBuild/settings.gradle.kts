// tag::repositories[]
pluginManagement {
    repositories {
        jcenter()
        gradlePluginPortal()
    }
}
// end::repositories[]

gradle.allprojects {
    buildscript {
        repositories { jcenter() }
    }
    repositories { jcenter() }
}

// tag::base[]
rootProject.name = "multi-project-build"
include("domain", "infra", "http")
// end::base[]
