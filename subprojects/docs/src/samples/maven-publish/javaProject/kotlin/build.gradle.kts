plugins {
    java
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

dependencies {
   compile("commons-collections:commons-collections:3.2.2")
}

repositories {
    mavenCentral()
}

// tag::publish-custom-artifact[]
tasks.register<Jar>("sourcesJar") {
    classifier = "sources"
    from(sourceSets.main.get().allJava)
}

tasks.register<Jar>("javadocJar") {
    classifier = "javadoc"
    from(tasks.javadoc.get().destinationDir)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
        }
    }
}
// end::publish-custom-artifact[]

// tag::repo-url-from-variable[]
// tag::repo-url-from-project-property[]
publishing {
    repositories {
        maven {
            val releasesRepoUrl = "$buildDir/repos/releases"
            val snapshotsRepoUrl = "$buildDir/repos/snapshots"
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
