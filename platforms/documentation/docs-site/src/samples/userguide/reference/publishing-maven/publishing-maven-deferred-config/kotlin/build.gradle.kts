plugins {
    id("java-library")
    id("maven-publish")
}

/*
// tag::deferred-config-before[]
subprojects {
    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = tasks.jar.get().archiveBaseName.get()
            }
        }
    }
}
// end::deferred-config-before[]
*/

// tag::deferred-config-after[]
subprojects {
    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                afterEvaluate {
                    artifactId = tasks.jar.get().archiveBaseName.get()
                }
            }
        }
    }
}
// end::deferred-config-after[]
