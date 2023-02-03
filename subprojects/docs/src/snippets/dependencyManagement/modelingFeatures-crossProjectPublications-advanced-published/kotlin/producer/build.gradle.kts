plugins {
    `java-library`
    `maven-publish`
    `instrumented-jars`
}

publishing {
    repositories {
        maven {
            setUrl("${buildDir}/repo")
        }
    }
    publications {
        create<MavenPublication>("myPublication") {
            from(components["myAdhocComponent"])
        }
    }
}

if (project.hasProperty("disableGradleMetadata")) {
    // tag::disable_gradle_metadata_publication[]
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
    // end::disable_gradle_metadata_publication[]
}

if (project.hasProperty("customRepository")) {
    // tag::gradle_metadata_source[]
    repositories {
        maven {
            setUrl("http://repo.mycompany.com/repo")
            metadataSources {
                gradleMetadata()
            }
        }
    }
    // end::gradle_metadata_source[]
}
