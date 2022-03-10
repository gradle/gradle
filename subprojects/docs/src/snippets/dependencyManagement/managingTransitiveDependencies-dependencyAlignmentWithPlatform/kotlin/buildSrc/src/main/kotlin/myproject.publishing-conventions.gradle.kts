plugins {
    id("maven-publish")
}

group = rootProject.group
version.set(rootProject.version)

publishing {
    repositories {
        maven {
            setUrl("${rootProject.buildDir}/repo")
        }
    }
}
