plugins {
    base
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

// tag::custom-artifact[]
val rpmFile = file("$buildDir/rpms/my-package.rpm")
val rpmArtifact = artifacts.add("archives", rpmFile) {
    type = "rpm"
    builtBy("rpm")
}
// end::custom-artifact[]

tasks.register("rpm") {
    outputs.file(rpmFile)
    doLast {
        // produce real RPM here
        rpmFile.writeText("file contents")
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
            url = uri("$buildDir/repo")
        }
    }
// tag::custom-artifact-publication[]
}
// end::custom-artifact-publication[]
