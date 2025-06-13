plugins {
    `application`
}

// tag::avoid-this[]
dependencies {
    runtimeOnly(project(":lib")) // <1>
}

abstract class BadClasspathPrinter : DefaultTask() {
    @get:InputFiles
    var classpath: Set<File> = emptySet() // <2>

    private fun calculateDigest(fileOrDirectory: File): Int {
        require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    fun run() {
        logger.lifecycle(
            classpath.joinToString("\n") {
                val digest = calculateDigest(it) // <4>
                "$it#$digest"
            }
        )
    }
}

tasks.register("badClasspathPrinter", BadClasspathPrinter::class) {
    classpath = configurations.named("runtimeClasspath").get().resolve() // <3>
}
// end::avoid-this[]
