// tag::plugins[]
plugins {
    `java-library`
    `maven-publish`
    id("com.example.java-convention")
}
// end::plugins[]

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
