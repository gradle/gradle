rootProject.name = "dependency-resolution-management-credentials"

// tag::credentials[]
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "internal"
            url = uri("https://repo.internal.acme.com/maven")
            credentials(PasswordCredentials::class)
        }
    }
}
// end::credentials[]
