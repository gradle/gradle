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
