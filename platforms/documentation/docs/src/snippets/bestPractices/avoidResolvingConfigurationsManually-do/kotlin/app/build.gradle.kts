plugins {
    `application`
}

// tag::do-this[]
dependencies {
    runtimeOnly(project(":lib")) // <1>
}

abstract class GoodClasspathPrinter : DefaultTask() {
    @get:InputFiles
    abstract val resolvedClasspath: ConfigurableFileCollection // <2>

    private fun calculateDigest(fileOrDirectory: File): Int {
        require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    fun run() {
        logger.lifecycle(
            resolvedClasspath.joinToString("\n") {
                val digest = calculateDigest(it) // <4>
                "$it#$digest"
            }
        )
    }
}

tasks.register("goodClasspathPrinter", GoodClasspathPrinter::class.java) {
    resolvedClasspath.from(configurations.named("runtimeClasspath")) // <3>
}
// end::do-this[]
