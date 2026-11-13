rootProject.name = "dependency-resolution-management-centralized"

// tag::centralized[]
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
// end::centralized[]
