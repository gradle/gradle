// tag::entire[]
pluginManagement {
    includeBuild("../plugin")
    repositories {
        gradlePluginPortal()
    }
}
// end::entire[]

/*
// tag::repo[]
pluginManagement {
    repositories {
        maven {
            url = file("../plugin/build/local-repo").toURI()
        }
        gradlePluginPortal()
    }
}
// end::repo[]
*/

// tag::entire[]
rootProject.name = "consumer"
// end::entire[]
