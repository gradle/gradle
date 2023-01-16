plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java compiler plugin used by Gradle's incremental compiler"

tasks.withType<JavaCompile>().configureEach {
    // TODO: Anze: Property<T>.assign should accept nullable value
    options.release.set(null as? Int)
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}
