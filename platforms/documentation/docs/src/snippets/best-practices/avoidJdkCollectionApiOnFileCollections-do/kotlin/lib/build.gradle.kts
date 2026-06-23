import org.gradle.api.artifacts.Configuration.State.RESOLVED

plugins {
    `java-library`
}

dependencies {
    api("org.apache.commons:commons-lang3:3.12.0")
}

// tag::do-this[]
abstract class FileCounterTask: DefaultTask() {
    @get:InputFiles
    abstract val countMe: ConfigurableFileCollection

    @TaskAction
    fun countFiles() {
        logger.lifecycle("Count: " + countMe.files.size)
    }
}

tasks.register<FileCounterTask>("goodCountingTask") {
    countMe.from(configurations.runtimeClasspath) // <1>
    countMe.from(layout.projectDirectory.file("extra.txt"))
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}
// end::do-this[]
