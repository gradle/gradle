plugins {
    id("base")
}

// tag::register-configure-implement[]
tasks.register<Copy>("myCopy")                              // <1>

tasks.named<Copy>("myCopy") {                               // <2>
    from("resources")
    into("target")
    include("**/*.txt", "**/*.xml", "**/*.properties")
}

abstract class MyCopyTask : DefaultTask() {                 // <3>
    @TaskAction
    fun copyFiles() {
        val sourceDir = File("sourceDir")
        val destinationDir = File("destinationDir")
        sourceDir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == "txt") {
                file.copyTo(File(destinationDir, file.name))
            }
        }
    }
}
// end::register-configure-implement[]
