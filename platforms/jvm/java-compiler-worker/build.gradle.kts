plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Classes required to implement Java compiler workers. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

gradlebuildJava {
    usesJdkInternals = true
}

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.daemonServerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.problemsApi)
    api(projects.stdlibJavaExtensions)

    api(projects.coreApi) {
        because("Compiler and WorkResult. We should migrate away from these interfaces.")
    }

    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.concurrent)
    implementation(projects.problemsRendering)

    testImplementation(testFixtures(projects.core))
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
