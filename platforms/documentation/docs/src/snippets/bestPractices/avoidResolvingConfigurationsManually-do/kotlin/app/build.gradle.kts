plugins {
    `application`
}

// tag::do-this[]
dependencies {
    runtimeOnly(project(":lib")) // <1>
}

abstract class GoodClasspathPrinter : DefaultTask() {
    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection // <2>

    private fun calculateDigest(fileOrDirectory: File): Int {
        require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    fun run() {
        logger.lifecycle(
            classpath.joinToString("\n") {
                val digest = calculateDigest(it) // <3>
                "$it#$digest"
            }
        )
    }
}

tasks.register("goodClasspathPrinter", GoodClasspathPrinter::class.java) {
    classpath.from(configurations.named("runtimeClasspath")) // <4>
}
// end::do-this[]
