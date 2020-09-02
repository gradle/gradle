plugins {
    id("maven-publish")
}

group = rootProject.group
version = rootProject.version

publishing {
    repositories {
        maven {
            setUrl("${rootProject.buildDir}/repo")
        }
    }
}
