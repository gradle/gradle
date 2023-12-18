rootProject.name = "tasks-with-dependency-resolution-result-inputs"
// tag::includes[]
includeBuild("dependency-reports")

include("utilities")
include("list")
// end::includes[]

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
