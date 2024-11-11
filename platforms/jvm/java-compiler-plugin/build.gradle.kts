plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java compiler plugin used by Gradle's incremental compiler"

jvmCompile {
    usesJdkInternals = true
}


tasks.withType<Test>().configureEach {
    // TODO: Delete after Gradle 9.0, used just to pass Gradleception tests
    fun Provider<JavaVersion>.isCompatibleWith(version: JavaVersion): Boolean =
        get().isCompatibleWith(version)

    // TODO: Delete after Gradle 9.0, used just to pass Gradleception tests
    operator fun ConfigurableFileCollection.plusAssign(fileCollection: FileCollection) {
        from(fileCollection)
    }

    if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}

// Java compiler plugin should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())
