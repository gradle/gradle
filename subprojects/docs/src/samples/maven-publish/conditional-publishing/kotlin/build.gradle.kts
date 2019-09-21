plugins {
    java
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allJava)
}

// tag::publishing[]
publishing {
    publications {
        create<MavenPublication>("binary") {
            from(components["java"])
        }
        create<MavenPublication>("binaryAndSources") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
        }
    }
    repositories {
        // change URLs to point to your repos, e.g. http://my.org/repo
        maven {
            name = "external"
            url = uri("$buildDir/repos/external")
        }
        maven {
            name = "internal"
            url = uri("$buildDir/repos/internal")
        }
    }
}
// end::publishing[]

// tag::task-config[]
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        (repository == publishing.repositories["external"] &&
            publication == publishing.publications["binary"]) ||
        (repository == publishing.repositories["internal"] &&
            publication == publishing.publications["binaryAndSources"])
    }
}
tasks.withType<PublishToMavenLocal>().configureEach {
    onlyIf {
        publication == publishing.publications["binaryAndSources"]
    }
}
// end::task-config[]

// tag::shorthand-tasks[]
tasks.register("publishToExternalRepository") {
    group = "publishing"
    description = "Publishes all Maven publications to the external Maven repository."
    dependsOn(tasks.withType<PublishToMavenRepository>().matching {
        it.repository == publishing.repositories["external"]
    })
}
// end::shorthand-tasks[]

tasks.register("publishForDevelopment") {
    group = "publishing"
    description = "Publishes all Maven publications to the internal Maven repository and the local Maven repository."
    dependsOn(tasks.withType<PublishToMavenRepository>().matching {
        it.repository == publishing.repositories["internal"]
    })
    dependsOn(tasks.withType<PublishToMavenLocal>())
}
