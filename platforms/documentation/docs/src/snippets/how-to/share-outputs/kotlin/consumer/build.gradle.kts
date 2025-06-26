// tag::custom-config[]
plugins {
    id("application")
}

// This configuration is used to declare dependencies only.
// It is neither resolvable nor consumable.
val instrumentedRuntimeDependencies by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

// This configuration is used to resolve the instrumented JAR files.
// It extends from the dependency-declaring configuration above.
val instrumentedRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true

    // Inherit the dependency declarations
    extendsFrom(instrumentedRuntimeDependencies)

    // These attributes must match the ones used by the producer's configuration
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        // The defining attribute
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
