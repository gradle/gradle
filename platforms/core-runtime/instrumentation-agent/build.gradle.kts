plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java agent implementation that instruments loaded classes"

gradleModule {
    targetRuntimes {
        // Agent's premain is invoked before main(), so it should not cause the startup to fail because of the too new class file format.
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "org.gradle.instrumentation.agent.Agent",
        ))
    }
}
