// tag::content-filtering-do[]
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                // Only use this repository for the androidx.activity group
                includeGroup("androidx.activity")
            }
        }
        mavenCentral()
    }
}
// end::content-filtering-do[]

rootProject.name = "content-filtering-exclusive-do"
