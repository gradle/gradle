plugins {
    base
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

// tag::custom-artifact[]
val rpmFile = layout.buildDirectory.file("rpms/my-package.rpm")
val rpmArtifact = artifacts.add("archives", rpmFile.get().asFile) {
    type = "rpm"
    builtBy("rpm")
}
// end::custom-artifact[]

tasks.register("rpm") {
    // Reduce scope of property for compatibility with the configuration cache
    val rpmFile = rpmFile
    outputs.file(rpmFile)
    doLast {
        // produce real RPM here
        rpmFile.get().asFile.writeText("file contents")
    }
}

// tag::custom-artifact-publication[]
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(rpmArtifact)
        }
    }
// end::custom-artifact-publication[]
    repositories {
        // change URLs to point to your repo, e.g. http://my.org/repo
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
// tag::custom-artifact-publication[]
}
// end::custom-artifact-publication[]
