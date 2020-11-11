// tag::pluginManagement[]
settingsEvaluated {
    pluginManagement {
        repositories {
            gradlePluginPortal()
        }
    }
}
// end::pluginManagement[]

// tag::sourceRepository[]
allprojects {
    repositories {
        jcenter()
    }
}
// end::sourceRepository[]
