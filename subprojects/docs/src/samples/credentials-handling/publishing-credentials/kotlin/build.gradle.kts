plugins {
    `java-library`
    `maven-publish`

    // this plugin comes from an included build - it fakes a maven repository to allow executing the authentication flow
    id("maven-repository-stub")
}

version = "1.0.2"
group = "com.example"

// tag::publication[]
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components.getByName("java"))
        }
    }
// tag::repositories[]
    repositories {
        // using the repository name to resolve credentials
        maven {
            name = "mySecureRepository"
            credentials(PasswordCredentials::class)
            // url = uri(<<some repository url>>)
        }
        // using an explicit identity to resolve credentials
        maven {
            name = "myAwesomeReleaseRepository"
            credentials(PasswordCredentials, "mySecureRepository")
            // url = uri(<<some repository url>>)
        }
    }
// end::repositories[]
}
// end::publication[]
