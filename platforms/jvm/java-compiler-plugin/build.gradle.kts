plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java compiler plugin used by Gradle's incremental compiler"

jvmCompile {
    compilations {
        named("main") {
            usesJdkInternals = true
        }
    }
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}

// Java compiler plugin should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())
