plugins {
    `java-library`
}

// tag::resolvable-configuration[]
val instrumentedClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
// end::resolvable-configuration[]

// tag::explicit-configuration-dependency[]
dependencies {
    instrumentedClasspath(project(mapOf(
        "path" to ":producer",
        "configuration" to "instrumentedJars")))
}
// end::explicit-configuration-dependency[]

tasks.register("resolveInstrumentedClasses") {
    inputs.files(instrumentedClasspath)
    doLast {
        println(instrumentedClasspath.files.map { it.name })
    }
}
