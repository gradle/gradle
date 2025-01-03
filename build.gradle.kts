import org.gradle.util.internal.GUtil

plugins {
    id("gradlebuild.root-build")

    id("gradlebuild.teamcity-import-test-data")  // CI: Import Test tasks' JUnit XML if they're UP-TO-DATE or FROM-CACHE
    id("gradlebuild.lifecycle")                  // CI: Add lifecycle tasks to for the CI pipeline (currently needs to be applied early as it might modify global properties)
    id("gradlebuild.generate-subprojects-info")  // CI: Generate subprojects information for the CI testing pipeline fan out
    id("gradlebuild.cleanup")                    // CI: Advanced cleanup after the build (like stopping daemons started by tests)

    id("gradlebuild.update-versions")            // Local development: Convenience tasks to update versions in this build: 'released-versions.json', 'agp-versions.properties', ...
    id("gradlebuild.wrapper")                    // Local development: Convenience tasks to update the wrapper (like 'nightlyWrapper')
}

description = "Adaptable, fast automation for all"

dependencyAnalysis {
    issues {
        all {
            onUnusedAnnotationProcessors {
                // Ignore check for internal-instrumentation-processor, since we apply
                // it to all distribution.api-java projects but projects might not have any upgrade
                exclude(":internal-instrumentation-processor")
            }
            ignoreSourceSet("archTest", "crossVersionTest", "docsTest", "integTest", "jmh", "peformanceTest", "smokeTest", "testInterceptors", "testFixtures", "smokeIdeTest")
        }
    }
}

tasks.register("checkWrapperVersion") {
    group = "verification"
    val distributionUrl = tasks.named<Wrapper>("wrapper").map { task ->
        GUtil.loadProperties(task.propertiesFile).getProperty("distributionUrl")
    }

    doLast {
        val configuredVersion = distributionUrl.get().substringAfterLast("gradle-").substringBeforeLast("-")
        val wrapperGradleVersion = GradleVersion.version(configuredVersion)
        if (wrapperGradleVersion.isSnapshot) {
            throw GradleException("Wrapper version ${wrapperGradleVersion.version} is not a long-lived version. Please use GA, RC, or milestone version.")
        }
    }
}
