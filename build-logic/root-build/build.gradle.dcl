kotlinDslPlugin {
    description = "Provides plugins that configures the root Gradle project"

    dependencies {
        implementation("gradlebuild:basics")

        implementation(project(":idea"))
        implementation(project(":profiling"))

        implementation(project(":cleanup")) {
            because("The CachesCleaner service is shared and needs to be on the root classpath")
        }

        implementation(catalog("buildLibs.dependencyAnalysisPlugin"))
    }
}
