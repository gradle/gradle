plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A Java agent implementation that instruments loaded classes"

dependencies {
    compileOnly(libs.jspecify)
}

gradleModule {
    requiredRuntimes {
        client = true
        daemon = true
        worker = true
    }
    computedRuntimes {
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Premain-Class" to "org.gradle.instrumentation.agent.Agent",
        ))
    }
}

errorprone {
    nullawayEnabled = true
}
