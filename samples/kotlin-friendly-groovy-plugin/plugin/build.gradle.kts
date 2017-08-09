import org.gradle.api.tasks.bundling.Jar

plugins {
    `java-gradle-plugin`
    `maven-publish`
    groovy
}

group = "my"

version = "1.0"

gradlePlugin {
    (plugins) {
        "documentation" {
            id = "my.documentation"
            implementationClass = "my.DocumentationPlugin"
        }
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

publishing {
    (publications) {
        "mavenSources"(MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri("build/repository")
        }
    }
}
