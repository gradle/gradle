plugins {
    id("java-library")
}

version = "1.0"

val buildInfo by tasks.registering(BuildInfo::class) {
    version.set(project.version.toString())
    outputFile.set(file("$buildDir/generated-resources/build-info.properties"))
}

sourceSets {
    main {
        output.dir(buildInfo.get().outputFile.getAsFile().get().parentFile, "builtBy" to buildInfo)
    }
}
