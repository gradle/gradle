plugins {
    id("java-library")
}

// tag::timestamp[]
version = "3.2-${System.currentTimeMillis()}"

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Version" to project.version))
    }
}
// end::timestamp[]
