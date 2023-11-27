plugins {
    `java-library`
}

// tag::assignment[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

abstract class WriteJavaVersionTask : DefaultTask() {
    @get:Input
    abstract val javaVersion: Property<String>
    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun execute() {
        output.get().asFile.writeText("Java version: ${javaVersion.get()}")
    }
}

tasks.register<WriteJavaVersionTask>("writeJavaVersion") {
    javaVersion.set("17") // <1>
    javaVersion = "17" // <2>
    javaVersion = java.toolchain.languageVersion.map { it.toString() } // <3>
    output = layout.buildDirectory.file("writeJavaVersion/javaVersion.txt")
}
// end::assignment[]
