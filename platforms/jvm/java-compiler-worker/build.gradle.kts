plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains logic for compiling java source files. May execute within a separate worker process."

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.fileCollections)
    api(projects.jvmCompilerWorker)
    api(projects.problemsApi)
    api(projects.stdlibJavaExtensions)
    api(libs.guava)
    api(libs.inject)
    api(libs.jspecify)
    api(libs.slf4jApi)

    implementation(projects.concurrent)
    implementation(projects.core)
    implementation(projects.loggingApi)
    implementation(projects.problemsRendering)

    testImplementation(testFixtures(projects.core))
}

jvmCompile {
    compilations {
        named("main") {
            usesJdkInternals = true
        }
    }
}

tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        // This project accesses JDK internals, which we need to open up so that javadoc can access them
        addMultilineStringsOption("-add-exports").value = listOf(
            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
        )
    }
}
