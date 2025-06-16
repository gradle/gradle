import org.gradle.api.artifacts.Configuration.State.RESOLVED

plugins {
    `java-library`
}

dependencies {
    api("org.apache.commons:commons-lang3:3.12.0")
}

// tag::do-this[]
tasks.register<Zip>("goodZippingTask") {
    from(configurations.runtimeClasspath) // <1>
    from(layout.projectDirectory.file("extra.txt"))
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}
// end::do-this[]
