/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild

tasks.registerCITestDistributionLifecycleTasks()
tasks.registerEarlyFeedbackLifecycleTasks()
tasks.named("quickTest") {
    dependsOn("test")
}
tasks.named("platformTest") {
    dependsOn("test")
}
tasks.configureCIIntegrationTestDistributionLifecycleTasks()
tasks.configureCICrossVersionTestDistributionLifecycleTasks()

val ciGroup = "CI Lifecycle"

/**
 * Test lifecycle tasks that correspond to CIBuildModel.TestType (see .teamcity/Gradle_Check/model/CIBuildModel.kt).
 */
fun TaskContainer.registerCITestDistributionLifecycleTasks() {
    register("quickTest") {
        description = "Run all unit, integration and cross-version (against latest release) tests in embedded execution mode"
        group = ciGroup
    }

    register("platformTest") {
        description = "Run all unit, integration and cross-version (against latest release) tests in forking execution mode"
        group = ciGroup
    }

    register("quickFeedbackCrossVersionTest") {
        description = "Run cross-version tests against a limited set of versions"
        group = ciGroup
    }

    register("allVersionsCrossVersionTest") {
        description = "Run cross-version tests against all released versions (latest patch release of each)"
        group = ciGroup
    }

    register("allVersionsIntegMultiVersionTest") {
        description = "Run all multi-version integration tests with all version to cover"
        group = ciGroup
    }

    register("parallelTest") {
        description = "Run all integration tests in parallel execution mode: each Gradle execution started in a test run with --parallel"
        group = ciGroup
    }

    register("noDaemonTest") {
        description = "Run all integration tests in no-daemon execution mode: each Gradle execution started in a test forks a new daemon"
        group = ciGroup
    }

    register("configCacheTest") {
        description = "Run all integration tests with instant execution"
        group = ciGroup
    }

    register("watchFsTest") {
        description = "Run all integration tests with file system watching enabled"
        group = ciGroup
    }

    register("forceRealizeDependencyManagementTest") {
        description = "Runs all integration tests with the dependency management engine in 'force component realization' mode"
        group = ciGroup
    }
}

/**
 * Basic compile and check lifecycle tasks.
 */
fun TaskContainer.registerEarlyFeedbackLifecycleTasks() {
    register("compileAllBuild") {
        description = "Initialize CI Pipeline by priming the cache before fanning out"
        group = ciGroup
        dependsOn("compileAll")
    }

    register("sanityCheck") {
        description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
        group = "verification"
        dependsOn("compileAll", "codeQuality")
    }
}

fun TaskContainer.configureCIIntegrationTestDistributionLifecycleTasks() {
    named("quickTest") {
        dependsOn("embeddedIntegTest")
    }

    named("platformTest") {
        dependsOn("forkingIntegTest")
    }

    named("allVersionsIntegMultiVersionTest") {
        dependsOn("integMultiVersionTest")
    }

    named("parallelTest") {
        dependsOn("parallelIntegTest")
    }

    named("noDaemonTest") {
        dependsOn("noDaemonIntegTest")
    }

    named("configCacheTest") {
        dependsOn("configCacheIntegTest")
    }

    named("watchFsTest") {
        dependsOn("watchFsIntegTest")
    }

    named("forceRealizeDependencyManagementTest") {
        dependsOn("integForceRealizeTest")
    }
}

fun TaskContainer.configureCICrossVersionTestDistributionLifecycleTasks() {
    named("quickTest") {
        dependsOn("embeddedCrossVersionTest")
    }

    named("platformTest") {
        dependsOn("forkingCrossVersionTest")
    }

    named("quickFeedbackCrossVersionTest") {
        dependsOn("quickFeedbackCrossVersionTests")
    }

    named("allVersionsCrossVersionTest") {
        dependsOn("allVersionsCrossVersionTests")
    }
}
