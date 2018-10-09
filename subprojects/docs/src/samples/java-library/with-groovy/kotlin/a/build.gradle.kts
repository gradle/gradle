plugins {
    `java-library`
    groovy
}

dependencies {
    compile(localGroovy())
}

// tag::configure-groovy[]
configurations {
    apiElements {
        val compileGroovy = tasks.compileGroovy.get()
        outgoing.variants["classes"].artifact(mapOf(
            "file" to compileGroovy.destinationDir,
            "type" to ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            "builtBy" to compileGroovy)
        )
    }
}
// end::configure-groovy[]
