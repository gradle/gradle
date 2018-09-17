// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    java
// tag::use-plugin[]
    maven
}
// end::use-plugin[]

group = "gradle"
version = "1.0"

// Configure the repository

tasks {
    "uploadArchives"(Upload::class) {
        repositories.withGroovyBuilder {
            "mavenDeployer" {
                "repository"("url" to uri("pomRepo"))
            }
        }
    }
}
