plugins {
    java
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0"

task<Jar>("sourcesJar") {
    archiveClassifier = "sources"
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
            url = uri(layout.buildDirectory.dir("repos/external"))
        }
        maven {
            name = "internal"
            url = uri(layout.buildDirectory.dir("repos/internal"))
        }
    }
}
// end::publishing[]

// tag::task-config[]
tasks.withType<PublishToMavenRepository>().configureEach {
    val predicate = repository.zip(publication) { repo, pub ->
        (repo == publishing.repositories["external"] && pub == publishing.publications["binary"])
            || (repo == publishing.repositories["internal"] && pub == publishing.publications["binaryAndSources"])
    }
    onlyIf("publishing binary to the external repository, or binary and sources to the internal one") {
        predicate.get()
    }
}
tasks.withType<PublishToMavenLocal>().configureEach {
    val predicate = publication.map { pub ->
        pub == publishing.publications["binaryAndSources"]
    }
    onlyIf("publishing binary and sources") {
        predicate.get()
    }
}
// end::task-config[]

// tag::shorthand-tasks[]
tasks.register("publishToExternalRepository") {
    group = "publishing"
    description = "Publishes all Maven publications to the external Maven repository."
    dependsOn(tasks.withType<PublishToMavenRepository>().matching {
        it.repository.get() == publishing.repositories["external"]
    })
}
// end::shorthand-tasks[]

tasks.register("publishForDevelopment") {
    group = "publishing"
    description = "Publishes all Maven publications to the internal Maven repository and the local Maven repository."
    dependsOn(tasks.withType<PublishToMavenRepository>().matching {
        it.repository.get() == publishing.repositories["internal"]
    })
    dependsOn(tasks.withType<PublishToMavenLocal>())
}
