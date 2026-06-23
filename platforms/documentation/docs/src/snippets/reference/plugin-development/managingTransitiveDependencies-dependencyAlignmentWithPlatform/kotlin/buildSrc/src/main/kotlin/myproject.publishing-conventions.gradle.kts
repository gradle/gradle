plugins {
    id("maven-publish")
}

group = rootProject.group
version = rootProject.version

publishing {
    repositories {
        maven {
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
}
