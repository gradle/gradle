import javax.inject.Inject;

abstract class CustomTaskUsingToolchains : DefaultTask {

    @get:Nested
    abstract val launcher: Property<JavaLauncher>

    @Inject
    constructor() {
        // Access the default toolchain
        val toolchain = project.extensions.getByType<JavaPluginExtension>().toolchain

        // acquire a provider that returns the launcher for the toolchain
        val service = project.extensions.getByType<JavaToolchainService>()
        val defaultLauncher = service.launcherFor(toolchain)

        // use it as our default for the property
        launcher.convention(defaultLauncher);
    }

    @TaskAction
    fun showConfiguredToolchain() {
        println(launcher.get().executablePath)
        println(launcher.get().metadata.installationPath)
    }
}

plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.register<CustomTaskUsingToolchains>("showDefaultToolchain")

tasks.register<CustomTaskUsingToolchains>("showCustomToolchain") {
    launcher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(14))
    })
}
