// tag::content-filtering-do[]
dependencyResolutionManagement {
    repositories {
        google {
            content {
                // Only use this repository for the androidx.activity group
                includeGroup("androidx.activity")
            }
        }
        // Specify the general repository last
        mavenCentral()
    }
}
// end::content-filtering-do[]

rootProject.name = "content-filtering-do"
