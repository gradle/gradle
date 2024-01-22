
// tag::use-plugin[]
plugins {
    id("maven-publish")
// end::use-plugin[]
    id("java-library")
// tag::use-plugin[]
}
// end::use-plugin[]

group = "org.gradle.sample"
version = "1.0"

// tag::publish-component[]
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
// end::publish-component[]
// tag::repositories[]
publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
// end::repositories[]

