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
    repositories {
        maven {
            name = "mySecure"
            // url = uri(<<some repository url>>)
        }
    }
}
// end::publication[]

// tag::credentials[]
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name == "publishLibraryPublicationToMySecureRepository" }) {
        val MAVEN_USERNAME_PROPERTY = "mavenUser"
        val MAVEN_PASSWORD_PROPERTY = "mavenPassword"
        val mavenUser = providers.gradleProperty(MAVEN_USERNAME_PROPERTY)
        val mavenPassword = providers.gradleProperty(MAVEN_PASSWORD_PROPERTY)
        if (!mavenUser.isPresent || !mavenPassword.isPresent) {
            throw GradleException("Publishing requires '$MAVEN_USERNAME_PROPERTY' and '$MAVEN_PASSWORD_PROPERTY' properties")
        }
        publishing.repositories.named<MavenArtifactRepository>("mySecure") {
            credentials {
                username = mavenUser.get()
                password = mavenPassword.get()
            }
        }
    }
}
// end::credentials[]
