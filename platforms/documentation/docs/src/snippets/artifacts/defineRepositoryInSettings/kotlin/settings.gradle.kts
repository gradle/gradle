rootProject.name = "define-repository-in-settings"

// tag::declare_repositories_settings[]
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
// end::declare_repositories_settings[]

// tag::prefer_settings[]
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
}
// end::prefer_settings[]

// tag::enforce_settings[]
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}
// end::enforce_settings[]

// tag::prefer_projects[]
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
}
// end::prefer_projects[]
