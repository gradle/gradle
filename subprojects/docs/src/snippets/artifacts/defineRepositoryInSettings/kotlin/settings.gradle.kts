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
    preferSettingsRepositories()
}
// end::prefer_settings[]

// tag::enforce_settings[]
dependencyResolutionManagement {
    enforceSettingsRepositories()
}
// end::enforce_settings[]

// tag::prefer_projects[]
dependencyResolutionManagement {
    preferProjectRepositories()
}
// end::prefer_projects[]
