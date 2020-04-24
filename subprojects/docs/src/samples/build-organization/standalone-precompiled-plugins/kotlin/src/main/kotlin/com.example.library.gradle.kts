// Define Java Library conventions for this organization.
// Projects need to use the organizations Java conventions and publish using Maven Publish

// tag::plugins[]
plugins {
    `java-library`
    `maven-publish`
    id("com.example.java-convention")
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
            url = uri("build/my-repo")
        }
    }
}
