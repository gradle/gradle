plugins {
    `java-library`
    `maven-publish`
    eclipse
    // See ??? for IntelliJ support
}

version = "1.0.2"
group = "org.gradle.sample"

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components.getByName("java"))
        }
    }
    repositories {
        maven {
            url = uri("${buildDir}/publishing-repository")
        }
    }
}
