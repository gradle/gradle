// tag::java-lib[]
plugins {
    id("java-library")
}
// end::java-lib[]

// tag::instrumentedjar-task[]
// Register a custom JAR task that packages the output of the 'main' source set.
// This JAR will have a classifier of 'instrumented' to distinguish it from the default artifact.
val instrumentedJar by tasks.registering(Jar::class) {
    archiveClassifier.set("instrumented")
    from(sourceSets.main.get().output)
    // Additional instrumentation processing could go here
}
// end::instrumentedjar-task[]

// tag::custom-config[]
configurations {
    // Create a custom consumable configuration named 'instrumentedJars'
    // This allows the producer to supply the instrumented JAR variant to other projects
    consumable("instrumentedJars") {
        // Assign attributes so that consuming projects can match on these
        attributes {
            // The unique attribute allows targeted selection
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
        }
    }
}

// Add the custom JAR artifact to the 'instrumentedJars' configuration
artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::custom-config[]
