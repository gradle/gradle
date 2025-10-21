// tag::custom-config[]
plugins {
    id("application")
}

// This configuration is used to declare dependencies only.
// It is neither resolvable nor consumable.
val instrumentedRuntimeDependencies by configurations.dependencyScope("instrumentedRuntimeDependencies")

// This resolvable configuration is used to resolve the instrumented JAR files.
// It extends from the dependency-declaring configuration above.
val instrumentedRuntime by configurations.resolvable("instrumentedRuntime") {
    // Wire the dependency declarations
    extendsFrom(instrumentedRuntimeDependencies)

    // These attributes must be compatible with the producer
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
    }
}
// end::custom-config[]

// tag::dependency[]
dependencies {
    // Declare a project dependency on the producer's instrumented output
    instrumentedRuntimeDependencies(project(":producer"))
}
// end::dependency[]

// tag::task[]
tasks.register<JavaExec>("runWithInstrumentation") {
    // Use the resolved instrumented classpath
    classpath = instrumentedRuntime
    mainClass.set("com.example.Main")
}
// end::task[]
