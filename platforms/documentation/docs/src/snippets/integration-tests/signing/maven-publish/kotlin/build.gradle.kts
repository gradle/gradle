// tag::all[]
// tag::use-plugin[]
plugins {
// end::use-plugin[]
    `java-library`
    `maven-publish`
// tag::use-plugin[]
    signing
}
// end::use-plugin[]

group = "com.example"
version = "1.0"

// tag::defining-sources-jar-task[]
java {
    withJavadocJar()
    withSourcesJar()
}
// end::defining-sources-jar-task[]

// tag::pom-customization[]
// tag::versions-resolved[]
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
// end::versions-resolved[]
// end::pom-customization[]
            artifactId = "my-library"
            from(components["java"])
// tag::versions-resolved[]
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
// end::versions-resolved[]
// tag::pom-customization[]
            pom {
                name = "My Library"
                description = "A concise description of my library"
                url = "http://www.example.com/library"
                properties = mapOf(
                    "myProp" to "value",
                    "prop.with.dots" to "anotherValue"
                )
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "johnd"
                        name = "John Doe"
                        email = "john.doe@example.com"
                    }
                }
                scm {
                    connection = "scm:git:git://example.com/my-library.git"
                    developerConnection = "scm:git:ssh://example.com/my-library.git"
                    url = "http://example.com/my-library/"
                }
            }
// tag::versions-resolved[]
        }
    }
// end::versions-resolved[]
// end::pom-customization[]
    repositories {
        maven {
            // change URLs to point to your repos, e.g. http://my.org/repo
            val releasesRepoUrl = uri(layout.buildDirectory.dir("repos/releases"))
            val snapshotsRepoUrl = uri(layout.buildDirectory.dir("repos/snapshots"))
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
// tag::pom-customization[]
// tag::versions-resolved[]
}
// end::versions-resolved[]
// end::pom-customization[]

// tag::sign-publication[]
signing {
    sign(publishing.publications["mavenJava"])
}
// end::sign-publication[]

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
// end::all[]
