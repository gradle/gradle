// tag::all[]
plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "com.example"
version = "1.0"

task<Jar>("sourcesJar") {
    from(sourceSets["main"].allJava)
    classifier = "sources"
}

task<Jar>("javadocJar") {
    from(tasks["javadoc"])
    classifier = "javadoc"
}

// tag::pom-customization[]
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // end::pom-customization[]
            artifactId = "my-library"
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
// tag::pom-customization[]
            pom {
                name.set("My Library")
                description.set("A concise description of my library")
                url.set("http://www.example.com/library")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("johnd")
                        name.set("John Doe")
                        email.set("john.doe@example.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://example.com/my-library.git")
                    developerConnection.set("scm:git:ssh://example.com/my-library.git")
                    url.set("http://example.com/my-library/")
                }
            }
        }
    }
// end::pom-customization[]
    repositories {
        maven {
            // change URLs to point to your repos, e.g. http://my.org/repo
            val releasesRepoUrl = uri("$buildDir/repos/releases")
            val snapshotsRepoUrl = uri("$buildDir/repos/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
// tag::pom-customization[]
}
// end::pom-customization[]

// tag::sign-publication[]
signing {
    sign(publishing.publications["mavenJava"])
}
// end::sign-publication[]

tasks.getByName<Javadoc>("javadoc") {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
// end::all[]
