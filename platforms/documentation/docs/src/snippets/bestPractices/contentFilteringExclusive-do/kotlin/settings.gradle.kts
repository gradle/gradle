// tag::content-filtering-do[]
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                // Only use this repository, and use this repository only, for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
// end::content-filtering-do[]

rootProject.name = "content-filtering-exclusive-do"
