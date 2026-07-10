plugins {
    `java-gradle-plugin`
    `maven-publish`
}

// tag::publishing-block[]
publishing {
    repositories {
        maven {
            url = uri("${layout.projectDirectory}/publish")
        }
    }
}
// end::publishing-block[]
