import org.gradle.api.artifacts.Configuration.State.RESOLVED

plugins {
    `java-library`
}

dependencies {
    api("org.apache.commons:commons-lang3:3.12.0")
}

// tag::avoid-this[]
abstract class FileCounterTask: DefaultTask() {
    @get:InputFiles
    abstract val countMe: ConfigurableFileCollection

    @TaskAction
    fun countFiles() {
        logger.lifecycle("Count: " + countMe.files.size)
    }
}

tasks.register<FileCounterTask>("badCountingTask") {
    if (!configurations.runtimeClasspath.get().isEmpty()) { // <1>
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
        countMe.from(configurations.runtimeClasspath)
    }
}

tasks.register<FileCounterTask>("badCountingTask2") {
    val files = configurations.runtimeClasspath.get().files // <2>
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}

tasks.register<FileCounterTask>("badCountingTask3") {
    val files = configurations.runtimeClasspath.get() + layout.projectDirectory.file("extra.txt") // <3>
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}

tasks.register<Zip>("badZippingTask") { // <4>
    if (!configurations.runtimeClasspath.get().isEmpty()) {
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
        from(configurations.runtimeClasspath)
    }
}
// end::avoid-this[]
