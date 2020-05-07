plugins {
    id("java-library")
    id("maven-publish")
}

version = "1.0.2"
group = "com.example"

val MAVEN_USERNAME_PROPERTY = "mavenUser"
val MAVEN_PASSWORD_PROPERTY = "mavenPassword"
val mavenUser = project.getProviders().gradleProperty(MAVEN_USERNAME_PROPERTY)
val mavenPassword = project.getProviders().gradleProperty(MAVEN_PASSWORD_PROPERTY)

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components.getByName("java"))
        }
    }
    repositories {
        maven {
            name = "mySecure"
            url = uri(com.example.MavenHttpRepository.address())
            credentials {
                username = mavenUser.orNull
                password = mavenPassword.orNull
            }
        }
    }
}

tasks.named("publishLibraryPublicationToMySecureRepository") {
    doFirst {
        if (!mavenUser.isPresent() || !mavenPassword.isPresent()) {
            throw GradleException(String.format("Publishing requires '%s' and '%s' properties",
                MAVEN_USERNAME_PROPERTY, MAVEN_PASSWORD_PROPERTY))
        }
    }
}

tasks.named("publishLibraryPublicationToMySecureRepository") {
    doFirst {
        com.example.MavenHttpRepository.start()
    }
    doLast {
        com.example.MavenHttpRepository.stop()
    }
}

