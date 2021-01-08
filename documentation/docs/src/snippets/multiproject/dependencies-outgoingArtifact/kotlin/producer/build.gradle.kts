plugins {
    id("java-library")
}

version = "1.0"

val buildInfo by tasks.registering(BuildInfo::class) {
    version.set(project.version.toString())
    outputFile.set(layout.buildDirectory.file("generated-resources/build-info.properties"))
}

sourceSets {
    main {
        output.dir(buildInfo.map { it.outputFile.asFile.get().parentFile })
    }
}
