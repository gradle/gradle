plugins {
    id("maven-publish")
}

group = rootProject.group
version = rootProject.version

publishing {
    repositories {
        maven {
            url = rootProject.layout.buildDirectory.dir("repo")
        }
    }
}
