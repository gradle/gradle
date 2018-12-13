// Using Ant from Gradle via the Ant Groovy Builder
// See https://docs.gradle.org/current/userguide/ant.html

tasks {
    register("hello") {
        group = "sample"
        doLast {
            ant.withGroovyBuilder {
                "echo"("message" to "Hello from Ant!")
            }
        }
    }

    register("zip") {
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


// Using custom Ant tasks from resolved dependency

repositories {
    jcenter()
}

val pmd by configurations.creating

dependencies {
    pmd("pmd:pmd:4.2.5")
}

tasks {
    register("pmd") {
        group = "sample"
        doLast {
            ant.withGroovyBuilder {
                "taskdef"("name" to "pmd", "classname" to "net.sourceforge.pmd.ant.PMDTask", "classpath" to pmd.asPath)
                "pmd"("shortFilenames" to true, "failonruleviolation" to true, "rulesetfiles" to "") {
                    "formatter"("type" to "text", "toConsole" to "true")
                    "fileset"("dir" to "src")
                }
            }
        }
    }
}
