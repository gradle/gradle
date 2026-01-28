plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.performance-testing")
    id("gradlebuild.performance-templates")
}

description = "Performance tests for the Gradle build tool"

dependencies {
    performanceTestImplementation(projects.baseServices)
    performanceTestImplementation(projects.core)
    performanceTestImplementation(projects.internalTesting)
    performanceTestImplementation(projects.stdlibJavaExtensions)
    performanceTestImplementation(projects.toolingApi)

    performanceTestImplementation(testFixtures(projects.toolingApi))

    performanceTestImplementation(libs.commonsLang)
    performanceTestImplementation(libs.commonsIo)
    performanceTestImplementation(testLibs.gradleProfiler)
    performanceTestImplementation(testLibs.jettyServer)
    performanceTestImplementation(testLibs.jettyWebApp)
    performanceTestImplementation(testLibs.junit)
    performanceTestImplementation(testLibs.servletApi)

    performanceTestRuntimeOnly(projects.coreApi)
    performanceTestRuntimeOnly(testLibs.jetty)

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
