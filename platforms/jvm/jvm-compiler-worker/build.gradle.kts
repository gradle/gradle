plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Classes required to implement compiler workers that execute on a JVM. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

dependencies {
    api(projects.baseServices)
    api(projects.daemonServerWorker)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.classloaders)
    implementation(projects.stdlibJavaExtensions)

    implementation(projects.coreApi) {
        because("Compiler and WorkResult. We should migrate away from these interfaces.")
    }

    implementation(libs.guava)
}
