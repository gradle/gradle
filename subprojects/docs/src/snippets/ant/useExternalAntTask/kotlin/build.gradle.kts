tasks.register("check") {
    doLast {
        ant.withGroovyBuilder {
            "taskdef"("resource" to "checkstyletask.properties") {
                "classpath" {
                    "fileset"("dir" to "libs", "includes" to "*.jar")
                }
            }
            "checkstyle"("config" to "checkstyle.xml") {
                "fileset"("dir" to "src")
            }
        }
    }
}
