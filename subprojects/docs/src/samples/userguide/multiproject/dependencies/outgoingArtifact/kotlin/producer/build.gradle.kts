val buildInfo = task<BuildInfo>("buildInfo") {
    version = project.version.toString()
    outputFile = file("$buildDir/generated-resources/build-info.properties")
}

sourceSets {
    main {
        output.dir(buildInfo.outputFile.parentFile, "builtBy" to buildInfo)
    }
}
