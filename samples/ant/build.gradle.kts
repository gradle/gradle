// Using Ant from Gradle via the Ant Groovy Builder
// See https://docs.gradle.org/current/userguide/ant.html

tasks {
    "hello" {
        group = "sample"
        doLast {
            ant.withGroovyBuilder {
                "echo"("message" to "Hello from Ant!")
            }
        }
    }

    "zip" {
        group = "sample"
        doLast {
            ant.withGroovyBuilder {
                "zip"("destfile" to "$buildDir/archive.zip") {
                    "fileset"("dir" to "src") {
                        "include"("name" to "**/*.xml")
                        "include"("name" to "**/*.txt")
                    }
                }
            }
        }
    }
}