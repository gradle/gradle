// Define Java Library conventions for this organization.
// Projects need to use the organization's Java conventions and publish using Maven Publish

// tag::plugins[]
plugins {
    `java-library`
    `maven-publish`
    id("myproject.java-conventions")
}
// end::plugins[]

// Projects have the 'com.example' group by convention
group = "com.example"

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "myOrgPrivateRepo"
            url = uri(layout.buildDirectory.dir("my-repo"))
        }
    }
}

// The project requires libraries to have a README containing sections configured below
// tag::use-java-class[]
val readmeCheck by tasks.registering(com.example.ReadmeVerificationTask::class) {
    readme = layout.projectDirectory.file("README.md")
    readmePatterns = listOf("^## API$", "^## Changelog$")
}
// end::use-java-class[]

tasks.named("check") { dependsOn(readmeCheck) }
