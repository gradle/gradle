plugins {
    id("java-library")
}

// tag::customAction[]
tasks.jar {
    val runtimeClasspath: FileCollection = configurations.runtimeClasspath.get()
    doFirst {
        manifest {
            val classPath = runtimeClasspath.map { it.name }.joinToString(" ")
            attributes("Class-Path" to classPath)
        }
    }
}
// end::customAction[]
