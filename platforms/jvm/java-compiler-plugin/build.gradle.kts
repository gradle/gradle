plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java compiler plugin used by Gradle's incremental compiler"

gradlebuildJava {
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
tasks.isolatedProjectsIntegTest {
    enabled = false
}
