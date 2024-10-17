plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java compiler plugin used by Gradle's incremental compiler"

gradlebuildJava {
    usesJdkInternals = true
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
