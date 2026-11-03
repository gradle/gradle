/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.testing

import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.TestSelectionStrategy
import gradlebuild.basics.propertyBasedCaseCount
import gradlebuild.basics.propertyBasedTestSeed
import gradlebuild.basics.propertyBasedTestStrategy
import gradlebuild.basics.testDistributionDogfoodingTag
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem


/**
 * Configures Hegel for `@HegelTest` tests, and selects or drops them with `HegelTestFilter`
 * from `internal-property-based-testing`.
 *
 * Hegel reads its settings from `HEGEL_*` environment variables, but settings on `@HegelTest` win over them.
 * Skipping test tasks without any `@HegelTest` is done by [skipWhenNoSelectedTests].
 */
fun Test.configurePropertyBasedTests() {
    if (BuildEnvironment.isCiServer) {
        // Hegel's own CI detection doesn't work, since test JVMs don't get the CI environment variables.
        // The ci profile derandomizes inputs and disables the example database.
        environment("HEGEL_DEFAULT_PROFILE", "ci")
    } else {
        // Put the database in the build dir, rather than Hegel's default of .hegel
        environment("HEGEL_DATABASE", "build/hegel")
    }

    project.propertyBasedTestSeed.orNull?.let {
        environment("HEGEL_SEED", it)
        inputs.property("propertyBasedTestSeed", it)
    }
    project.propertyBasedCaseCount.orNull?.let {
        environment("HEGEL_TEST_CASES", it)
        inputs.property("propertyBasedCaseCount", it)
    }

    val strategy = project.propertyBasedTestStrategy
    if (strategy == TestSelectionStrategy.ONLY) {
        // A task can still run without any @HegelTest, e.g. when the source set only mentions dev.hegel,
        // or when the platform excludes them
        failOnNoDiscoveredTests.set(false)
        // Gradle checks this instead when the task has test filters, e.g. an exclude pattern
        filter.isFailOnNoMatchingTests = false
    }
    // Read by HegelTestFilter
    systemProperty(
        "org.gradle.test.hegel.filter",
        when {
            // Wins over only, since these tests can't run here at all
            isUnsupportedHegelTestPlatform() -> "exclude"
            else -> strategy.name.lowercase()
        }
    )
}


/**
 * Whether the test JVM runs on a platform hegel-jna has no native library for.
 *
 * hegel-jna ships natives for glibc Linux, Apple Silicon macOS and Windows. That rules out the
 * Alpine Test Distribution agents, which use musl, and Intel macOS.
 * Test Distribution macOS agents are not checked, since they are only selected by `os=macos`.
 */
fun Test.isUnsupportedHegelTestPlatform(): Boolean =
    if (usesTestDistribution()) {
        // The tag picks what TD agents are used, so if it's `alpine` this can't run
        project.testDistributionDogfoodingTag.orNull == "alpine"
    } else {
        OperatingSystem.current().isMacOsX && System.getProperty("os.arch") in setOf("x86_64", "amd64")
    }
