plugins {
    java
    `ivy-publish`
}

group = "org.gradle.sample"
version = "1.0"

publishing {
    publications {
        create<IvyPublication>("ivyJava") {
            from(components["java"])
        }
    }
    repositories {
        ivy {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
