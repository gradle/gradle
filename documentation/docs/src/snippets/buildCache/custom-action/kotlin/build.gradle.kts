plugins {
    id("java-library")
}

// tag::customAction[]
tasks.jar {
    doFirst {
        manifest {
            val classPath = configurations.runtimeClasspath.get().map { it.name }.joinToString(" ")
            attributes("Class-Path" to classPath)
        }
    }
}
// end::customAction[]
