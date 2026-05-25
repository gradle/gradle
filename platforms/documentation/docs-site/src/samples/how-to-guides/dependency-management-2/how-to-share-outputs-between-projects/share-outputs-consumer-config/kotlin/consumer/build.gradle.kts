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
