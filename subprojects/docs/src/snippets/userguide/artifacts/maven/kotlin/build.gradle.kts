// tag::upload-file[]
plugins {
    // end::upload-file[]
    java
// tag::upload-file[]
    maven
}

// tag::builder[]
// tag::customize-pom[]
// tag::multiple-poms[]
tasks.named<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            "repository"("url" to "file://localhost/tmp/myRepo/")
// end::upload-file[]
// end::multiple-poms[]
// end::builder[]
            "pom" {
                setProperty("version", "1.0Maven")
                setProperty("artifactId", "myMavenName")
            }
// end::customize-pom[]
// tag::builder[]
            "pom" {
                "project" {
                    "licenses" {
                        "license" {
                            setProperty("name", "The Apache Software License, Version 2.0")
                            setProperty("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                            setProperty("distribution", "repo")
                        }
                    }
                }
            }
// end::builder[]
// tag::multiple-poms[]
            "addFilter"("api") {
                getProperty("artifact").withGroovyBuilder { setProperty("name", "api") }
            }
            "addFilter"("service") {
                getProperty("artifact").withGroovyBuilder { setProperty("name", "service") }
            }
            "pom"("api")?.withGroovyBuilder { setProperty("version", "mySpecialMavenVersion") }
// tag::customize-pom[]
// tag::upload-file[]
// tag::builder[]
        }
    }
}
// end::customize-pom[]
// end::multiple-poms[]
// end::upload-file[]
// end::builder[]

// tag::upload-with-ssh[]
val deployerJars by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    deployerJars("org.apache.maven.wagon:wagon-ssh:2.2")
}

tasks.named<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            setProperty("configuration", deployerJars)
            "repository"("url" to "scp://repos.mycompany.com/releases") {
                "authentication"("userName" to "me", "password" to "myPassword")
            }
        }
    }
}
// end::upload-with-ssh[]

// tag::customize-installer[]
tasks.install {
    repositories.withGroovyBuilder {
        "mavenInstaller" {
            "pom" {
                setProperty("version", "1.0Maven")
                setProperty("artifactId", "myName")
            }
        }
    }
}
// end::customize-installer[]

// tag::mappings[]
tasks.register("mappings") {
    doLast {
        println(maven.conf2ScopeMappings.mappings)
    }
}
// end::mappings[]
