tasks.register<Copy>("retrieveRuntimeDependencies") {
    into(layout.buildDirectory.dir("libs"))
    from(configurations.runtimeClasspath)
}
