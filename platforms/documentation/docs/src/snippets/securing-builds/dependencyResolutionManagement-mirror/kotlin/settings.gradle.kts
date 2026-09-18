rootProject.name = "dependency-resolution-management-mirror"

// tag::mirror[]
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "internalMirror"
            url = uri("https://repo.internal.acme.com/maven-public")
            credentials(PasswordCredentials::class)
        }
    }
}
pluginManagement {
    repositories {
        maven {
            name = "internalMirror"
            url = uri("https://repo.internal.acme.com/gradle-plugins")
            credentials(PasswordCredentials::class)
        }
    }
}
// end::mirror[]
