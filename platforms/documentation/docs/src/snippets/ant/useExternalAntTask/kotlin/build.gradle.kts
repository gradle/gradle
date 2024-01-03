repositories {
    mavenCentral()
}

val checkstyle by configurations.creating

dependencies {
    "checkstyle"("com.puppycrawl.tools:checkstyle:9.3")
}

val download = tasks.register<Copy>("download") {
    from(checkstyle)
    into("libs")
}

// tag::configure-task[]
tasks.register("check") {
    val checkstyleConfig = file("checkstyle.xml")
    doLast {
        ant.withGroovyBuilder {
            "taskdef"("resource" to "com/puppycrawl/tools/checkstyle/ant/checkstyle-ant-task.properties") {
                "classpath" {
                    "fileset"("dir" to "libs", "includes" to "*.jar")
                }
            }
            "checkstyle"("config" to checkstyleConfig) {
                "fileset"("dir" to "src")
            }
        }
    }
}
// end::configure-task[]

tasks.named("check") {
    dependsOn(download)
}
