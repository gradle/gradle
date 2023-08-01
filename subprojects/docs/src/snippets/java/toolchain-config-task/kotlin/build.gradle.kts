import java.io.ByteArrayOutputStream

plugins {
    `java-base`
}


abstract class TaskThatRequiresJavaExecutable : DefaultTask() {

    @get:Internal
    abstract val javaExecutable: RegularFileProperty

    @get:Inject
    protected abstract val execOps: ExecOperations

    @TaskAction
    fun action() {
        val outputBytes = ByteArrayOutputStream()
        execOps.exec {
            executable = javaExecutable.get().asFile.absolutePath
            args = listOf("-version")
            standardOutput = outputBytes
            errorOutput = outputBytes
        }
        require(outputBytes.toString("UTF-8").contains("version \"11")) {
            "unexpected '${javaExecutable.get().asFile.absolutePath} -version' output"
        }
    }
}

tasks.register<TaskThatRequiresJavaExecutable>("sampleTask")
val TaskContainer.sampleTask
    get() = named<TaskThatRequiresJavaExecutable>("sampleTask")


abstract class TaskThatRequiresJavaHome : DefaultTask() {

    @get:Internal
    abstract val javaHome: DirectoryProperty

    @get:Inject
    protected abstract val execOps: ExecOperations

    @TaskAction
    fun action() {
        val outputBytes = ByteArrayOutputStream()
        val javaExecutable = javaHome.file("bin/java")
        execOps.exec {
            executable = javaExecutable.get().asFile.absolutePath
            args = listOf("-version")
            standardOutput = outputBytes
            errorOutput = outputBytes
        }
        require(outputBytes.toString("UTF-8").contains("version \"11")) {
            "unexpected '${javaExecutable.get().asFile.absolutePath} -version' output"
        }
    }
}

tasks.register<TaskThatRequiresJavaHome>("anotherSampleTask")
val TaskContainer.anotherSampleTask
    get() = named<TaskThatRequiresJavaHome>("anotherSampleTask")


abstract class TaskThatRequiresJavaCompiler : DefaultTask() {

    @get:Internal
    abstract val javaCompilerExecutable: RegularFileProperty

    @get:Inject
    protected abstract val execOps: ExecOperations

    @TaskAction
    fun action() {
        val outputBytes = ByteArrayOutputStream()
        execOps.exec {
            executable = javaCompilerExecutable.get().asFile.absolutePath
            args = listOf("-version")
            standardOutput = outputBytes
            errorOutput = outputBytes
        }
        require(outputBytes.toString("UTF-8").contains("javac 11")) {
            "unexpected '${javaCompilerExecutable.get().asFile.absolutePath} -version' output"
        }
    }
}

tasks.register<TaskThatRequiresJavaCompiler>("yetAnotherSampleTask")
val TaskContainer.yetAnotherSampleTask
    get() = named<TaskThatRequiresJavaCompiler>("yetAnotherSampleTask")


// tag::java-executable[]
// tag::java-home[]
val launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(11)
}
// end::java-executable[]
// end::java-home[]

// tag::java-executable[]

tasks.sampleTask {
    javaExecutable = launcher.map { it.executablePath }
}
// end::java-executable[]


// tag::java-home[]

tasks.anotherSampleTask {
    javaHome = launcher.map { it.metadata.installationPath }
}
// end::java-home[]


// tag::java-compiler[]
val compiler = javaToolchains.compilerFor {
    languageVersion = JavaLanguageVersion.of(11)
}

tasks.yetAnotherSampleTask {
    javaCompilerExecutable = compiler.map { it.executablePath }
}
// end::java-compiler[]
