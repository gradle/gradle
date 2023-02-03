plugins {
    java
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
}

repositories {
    mavenCentral()
}

// tag::publish-modify-component[]
java {
    withJavadocJar()
    withSourcesJar()
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["sourcesElements"]) {
    skip()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
// end::publish-modify-component[]

// tag::repo-url-from-variable[]
// tag::repo-url-from-project-property[]
publishing {
    repositories {
        maven {
            val releasesRepoUrl = layout.buildDirectory.dir("repos/releases")
            val snapshotsRepoUrl = layout.buildDirectory.dir("repos/snapshots")
// end::repo-url-from-variable[]
            url = uri(if (project.hasProperty("release")) releasesRepoUrl else snapshotsRepoUrl)
// end::repo-url-from-project-property[]
// tag::repo-url-from-variable[]
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
// tag::repo-url-from-project-property[]
        }
    }
}
// end::repo-url-from-project-property[]
// end::repo-url-from-variable[]
