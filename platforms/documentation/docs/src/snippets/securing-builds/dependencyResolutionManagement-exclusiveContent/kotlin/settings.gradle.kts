rootProject.name = "dependency-resolution-management-exclusive-content"

// tag::exclusive-content[]
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "internal"
                    url = uri("https://repo.internal.acme.com/maven")
                    credentials(PasswordCredentials::class)
                }
            }
            filter {
                includeGroupAndSubgroups("com.acme")
            }
        }
        mavenCentral()
    }
}
// end::exclusive-content[]
