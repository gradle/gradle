// Register a custom JAR task that packages the output of the 'main' source set.
// This JAR will have a classifier of 'instrumented' to distinguish it from the default artifact.
val instrumentedJar by tasks.registering(Jar::class) {
    archiveClassifier.set("instrumented")
    from(sourceSets.main.get().output)
    // Additional instrumentation processing could go here
}
