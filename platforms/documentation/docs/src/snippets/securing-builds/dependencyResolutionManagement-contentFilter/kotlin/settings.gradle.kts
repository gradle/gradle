rootProject.name = "dependency-resolution-management-content-filter"

// tag::content-filter[]
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral {
            content {
                excludeGroupAndSubgroups("com.acme")
            }
        }
        maven {
            name = "internal"
            url = uri("https://repo.internal.acme.com/maven")
            credentials(PasswordCredentials::class)
            content {
                includeGroupAndSubgroups("com.acme")
            }
        }
    }
}
// end::content-filter[]
