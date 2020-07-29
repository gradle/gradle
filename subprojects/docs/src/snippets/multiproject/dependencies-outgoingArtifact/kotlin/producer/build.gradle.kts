val buildInfo by tasks.registering(BuildInfo::class) {
    version = project.version.toString()
    outputFile = file("$buildDir/generated-resources/build-info.properties")
}

sourceSets {
    main {
        output.dir(buildInfo.get().outputFile.parentFile, "builtBy" to buildInfo)
    }
}
