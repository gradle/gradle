plugins {
    `java-library`
}

// tag::resolve-call[]
val resolvedClasspath = configurations.runtimeClasspath.get().resolve() // <1>
logger.lifecycle(resolvedClasspath.joinToString(", "))
// end::resolve-call[]

// tag::good-classpath-printer[]
abstract class ClasspathPrinter : DefaultTask() {
    @get:InputFiles
    abstract val resolvedClasspath: ConfigurableFileCollection

    @TaskAction
    fun run() {
        logger.lifecycle(resolvedClasspath.files.joinToString(", ")) // <2>
    }
}

tasks.register("classpathPrinter", ClasspathPrinter::class) {
    resolvedClasspath.from(configurations.runtimeClasspath) // <1>
}
// end::good-classpath-printer[]
