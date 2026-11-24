plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-testing")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(projects.internalIntegTesting)

    performanceTestImplementation(testFixtures(projects.toolingApi))

    performanceTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("All Gradle features have to be available.")
    }
    performanceTestLocalRepository(projects.toolingApi) {
        because("IDE tests use the Tooling API.")
    }
}

dependencyAnalysis {
    issues {
        onUnusedDependencies {
            exclude(testLibs.junitJupiter)
        }

        ignoreSourceSet(sourceSets.performanceTest.name)
    }
}

errorprone {
    nullawayEnabled = true
}
