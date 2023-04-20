plugins {
    `java-library`
    `maven-publish`
}

version = "1.0.2"
group = "org.gradle.sample"

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("publishing-repository"))
        }
    }
}
