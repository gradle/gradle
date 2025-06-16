import org.gradle.api.artifacts.Configuration.State.RESOLVED

plugins {
    `java-library`
}

dependencies {
    api("org.apache.commons:commons-lang3:3.12.0")
}

// tag::avoid-this[]
tasks.register<Zip>("badZippingTask") {
    if (!configurations.runtimeClasspath.get().isEmpty()) { // <1>
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
        from(configurations.runtimeClasspath)
    }
}

tasks.register<Zip>("badZippingTask2") {
    val files = configurations.runtimeClasspath.get().files // <2>
    from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}

tasks.register<Zip>("badZippingTask3") {
    val files = configurations.runtimeClasspath.get() + layout.projectDirectory.file("extra.txt") // <3>
    from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}
// end::avoid-this[]
