plugins {
    `java-library`
    `maven-publish`
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
            url = uri(com.example.MavenRepositoryStub.address())
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

tasks.named("publishLibraryPublicationToMySecureRepository") {
    doFirst {
        com.example.MavenRepositoryStub.start()
    }
    doLast {
        com.example.MavenRepositoryStub.stop()
    }
}
