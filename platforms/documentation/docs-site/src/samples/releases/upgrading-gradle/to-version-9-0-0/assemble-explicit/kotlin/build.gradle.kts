val specialJar = tasks.register<Jar>("specialJar") {
    from("foo")
}

val special = configurations.create("special") {
    // In previous versions, this would have been enough to build the specialJar
    // artifact when running assemble
    outgoing.artifact(specialJar)
}

// In Gradle 9.0.0, you need to add a dependency from the artifact to the assemble task
tasks.named("assemble") {
    dependsOn(special.artifacts)
}
