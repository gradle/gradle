// tag::content-filtering-do[]
dependencyResolutionManagement {
    repositories {
        google {
            content {
                // Use this repository for androidx and GMS dependencies
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.gms")
            }
        }
        // Specify the fallback repository last
        mavenCentral()
    }
}
// end::content-filtering-do[]

rootProject.name = "content-filtering-do"
