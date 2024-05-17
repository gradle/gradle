import java.util.Properties

plugins {
    id("java-library")
}

version = "3.2-${System.currentTimeMillis()}"

// tag::normalization[]
normalization {
    runtimeClasspath {
        ignore("build-info.properties")
    }
}
// end::normalization[]

// tag::versionInfo[]
val currentVersionInfo = tasks.register<CurrentVersionInfo>("currentVersionInfo") {
    version = project.version as String
    versionInfoFile = layout.buildDirectory.file("generated-resources/currentVersion.properties")
}

sourceSets.main.get().output.dir(currentVersionInfo.map { it.versionInfoFile.get().asFile.parentFile })

abstract class CurrentVersionInfo : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputFile
    abstract val versionInfoFile: RegularFileProperty

    @TaskAction
    fun writeVersionInfo() {
        val properties = Properties()
        properties.setProperty("latestMilestone", version.get())
        versionInfoFile.get().asFile.outputStream().use { out ->
            properties.store(out, null)
        }
    }
}
// end::versionInfo[]


