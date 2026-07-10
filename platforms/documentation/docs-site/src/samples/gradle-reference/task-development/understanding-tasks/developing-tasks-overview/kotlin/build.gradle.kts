tasks.register<Copy>("myCopy")                              // <1> Register the `myCopy` task of type `Copy` to let Gradle know we intend to use it in our build logic.

tasks.named<Copy>("myCopy") {                               // <2> Configure the registered `myCopy` task with the inputs and outputs it needs according to its [API](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/Copy.html).
    from("resources")
    into("target")
    include("**/*.txt", "**/*.xml", "**/*.properties")
}

abstract class MyCopyTask : DefaultTask() {                 // <3> Implement a custom task type called `MyCopyTask` which extends `DefaultTask` and defines the `copyFiles` task action.
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
