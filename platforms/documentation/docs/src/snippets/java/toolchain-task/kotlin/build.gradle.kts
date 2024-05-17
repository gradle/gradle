import javax.inject.Inject;

// tag::custom-toolchain-task-with-java[]
abstract class CustomTaskUsingToolchains : DefaultTask() {

    @get:Nested
    abstract val launcher: Property<JavaLauncher> // <1>

    init {
        val toolchain = project.extensions.getByType<JavaPluginExtension>().toolchain // <2>
        val defaultLauncher = javaToolchainService.launcherFor(toolchain) // <3>
        launcher.convention(defaultLauncher) // <4>
    }

    @TaskAction
    fun showConfiguredToolchain() {
        println(launcher.get().executablePath)
        println(launcher.get().metadata.installationPath)
    }

    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService
}
// end::custom-toolchain-task-with-java[]

// tag::custom-toolchain-task-with-java-usage[]
plugins {
    java
}

java {
    toolchain { // <1>
        languageVersion = JavaLanguageVersion.of(8)
    }
}

tasks.register<CustomTaskUsingToolchains>("showDefaultToolchain") // <2>

tasks.register<CustomTaskUsingToolchains>("showCustomToolchain") {
    launcher = javaToolchains.launcherFor { // <3>
        languageVersion = JavaLanguageVersion.of(17)
    }
}
// end::custom-toolchain-task-with-java-usage[]
