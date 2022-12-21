plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java agent implementation that instruments loaded classes"

// Agent's premain is invoked before main(), so it should not cause the startup to fail because of the too new class file format.
gradlebuildJava.usedForStartup()

dependencies {
    integTestDistributionRuntimeOnly(project(":distributions-core"))

    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":launcher"))
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "org.gradle.instrumentation.agent.Agent",
        ))
    }
}
