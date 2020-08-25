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

import gradlebuild.basics.BuildEnvironment
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

// Lifecycle tasks used to to fan out the build into multiple builds in a CI pipeline.

val ciGroup = "CI Lifecycle"

val compileAllBuild = "compileAllBuild"

val sanityCheck = "sanityCheck"

val quickTest = "quickTest"

val platformTest = "platformTest"

val quickFeedbackCrossVersionTest = "quickFeedbackCrossVersionTest"

val allVersionsCrossVersionTest = "allVersionsCrossVersionTest"

val allVersionsIntegMultiVersionTest = "allVersionsIntegMultiVersionTest"

val parallelTest = "parallelTest"

val noDaemonTest = "noDaemonTest"

val configCacheTest = "configCacheTest"

val watchFsTest = "watchFsTest"

val soakTest = "soakTest"

val ignoredSubprojects = listOf(
    "soak", // soak test
    "distributions-integ-tests", // test build distributions
    "architecture-test" // sanity check
)

val forceRealizeDependencyManagementTest = "forceRealizeDependencyManagementTest"


setupTimeoutMonitorOnCI()
setupGlobalState()

subprojects.filter { it.name !in ignoredSubprojects }.forEach { it.registerLifecycleTasks() }


tasks.registerDistributionsPromotionTasks()


fun Project.registerLifecycleTasks() {
    tasks.registerCITestDistributionLifecycleTasks()
    plugins.withId("gradlebuild.java-library") {
        tasks.registerEarlyFeedbackLifecycleTasks()
        tasks.named(quickTest) {
            dependsOn("test")
        }
        tasks.named(platformTest) {
            dependsOn("test")
        }
    }
    plugins.withId("gradlebuild.integration-tests") {
        tasks.configureCIIntegrationTestDistributionLifecycleTasks()
    }
    plugins.withId("gradlebuild.cross-version-tests") {
        tasks.configureCICrossVersionTestDistributionLifecycleTasks()
    }
}

/**
 * Print all stacktraces of running JVMs on the machine upon timeout. Helps us diagnose deadlock issues.
 */
fun setupTimeoutMonitorOnCI() {
    if (BuildEnvironment.isCiServer) {
        val timer = Timer(true).apply {
            schedule(timerTask {
                exec {
                    commandLine("${System.getProperty("java.home")}/bin/java",
                        rootProject.file("subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/timeout/JavaProcessStackTracesMonitor.java"))
                }
            }, determineTimeoutMillis())
        }
        gradle.buildFinished {
            timer.cancel()
        }
    }
}

fun determineTimeoutMillis() =
    if (isRequestedTask(compileAllBuild) || isRequestedTask(sanityCheck) || isRequestedTask(quickTest)) {
        TimeUnit.MINUTES.toMillis(30)
    } else {
        TimeUnit.MINUTES.toMillis(165) // 2h45m
    }

fun setupGlobalState() {
    if (needsToUseTestVersionsPartial()) {
        globalProperty("testVersions" to "partial")
    }
    if (needsToUseTestVersionsAll()) {
        globalProperty("testVersions" to "all")
    }
}

fun needsToUseTestVersionsPartial() = isRequestedTask(platformTest)

fun needsToUseTestVersionsAll() = isRequestedTask(allVersionsCrossVersionTest)
    || isRequestedTask(allVersionsIntegMultiVersionTest)
    || isRequestedTask(soakTest)

/**
 * Basic compile and check lifecycle tasks.
 */
fun TaskContainer.registerEarlyFeedbackLifecycleTasks() {
    register(compileAllBuild) {
        description = "Initialize CI Pipeline by priming the cache before fanning out"
        group = ciGroup
        dependsOn(":base-services:createBuildReceipt", "compileAll")
    }

    register(sanityCheck) {
        description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
        group = "verification"
        dependsOn(
            "compileAll", ":docs:checkstyleApi", "codeQuality", ":internal-build-reports:allIncubationReportsZip",
            ":architecture-test:checkBinaryCompatibility", ":docs:javadocAll",
            ":architecture-test:test", ":tooling-api:toolingApiShadedJar")
    }
}

/**
 * Task that are called by the (currently separate) promotion build running on CI.
 */
fun TaskContainer.registerDistributionsPromotionTasks() {
    register("packageBuild") {
        description = "Build production distros and smoke test them"
        group = "build"
        dependsOn(":distributions-full:verifyIsProductionBuildEnvironment", ":distributions-full:buildDists",
            ":distributions-integ-tests:forkingIntegTest", ":docs:releaseNotes", ":docs:incubationReport", ":docs:checkDeadInternalLinks")
    }
}

/**
 * Test lifecycle tasks that correspond to CIBuildModel.TestType (see .teamcity/Gradle_Check/model/CIBuildModel.kt).
 */
fun TaskContainer.registerCITestDistributionLifecycleTasks() {
    register(quickTest) {
        description = "Run all unit, integration and cross-version (against latest release) tests in embedded execution mode"
        group = ciGroup
    }

    register(platformTest) {
        description = "Run all unit, integration and cross-version (against latest release) tests in forking execution mode"
        group = ciGroup
    }

    register(quickFeedbackCrossVersionTest) {
        description = "Run cross-version tests against a limited set of versions"
        group = ciGroup
    }

    register(allVersionsCrossVersionTest) {
        description = "Run cross-version tests against all released versions (latest patch release of each)"
        group = ciGroup
    }

    register(allVersionsIntegMultiVersionTest) {
        description = "Run all multi-version integration tests with all version to cover"
        group = ciGroup
    }

    register(parallelTest) {
        description = "Run all integration tests in parallel execution mode: each Gradle execution started in a test run with --parallel"
        group = ciGroup
    }

    register(noDaemonTest) {
        description = "Run all integration tests in no-daemon execution mode: each Gradle execution started in a test forks a new daemon"
        group = ciGroup
    }

    register(configCacheTest) {
        description = "Run all integration tests with instant execution"
        group = ciGroup
    }

    register(watchFsTest) {
        description = "Run all integration tests with file system watching enabled"
        group = ciGroup
    }

    register(forceRealizeDependencyManagementTest) {
        description = "Runs all integration tests with the dependency management engine in 'force component realization' mode"
        group = ciGroup
    }
}

fun TaskContainer.configureCIIntegrationTestDistributionLifecycleTasks() {
    named(quickTest) {
        dependsOn("embeddedIntegTest")
    }

    named(platformTest) {
        dependsOn("forkingIntegTest")
    }

    named(allVersionsIntegMultiVersionTest) {
        dependsOn("integMultiVersionTest")
    }

    named(parallelTest) {
        dependsOn("parallelIntegTest")
    }

    named(noDaemonTest) {
        description = "Run all integration tests in no-daemon execution mode: each Gradle execution started in a test forks a new daemon"
        group = ciGroup
        dependsOn("noDaemonIntegTest")
    }

    named(configCacheTest) {
        dependsOn("configCacheIntegTest")
    }

    named(watchFsTest) {
        dependsOn("watchFsIntegTest")
    }

    named(forceRealizeDependencyManagementTest) {
        dependsOn("integForceRealizeTest")
    }
}

fun TaskContainer.configureCICrossVersionTestDistributionLifecycleTasks() {
    named(quickTest) {
        dependsOn("embeddedCrossVersionTest")
    }

    named(platformTest) {
        dependsOn("forkingCrossVersionTest")
    }

    named(quickFeedbackCrossVersionTest) {
        dependsOn("quickFeedbackCrossVersionTests")
    }

    named(allVersionsCrossVersionTest) {
        dependsOn("allVersionsCrossVersionTests")
    }
}

fun globalProperty(pair: Pair<String, Any>) {
    val propertyName = pair.first
    val value = pair.second
    if (hasProperty(propertyName)) {
        val otherValue = property(propertyName)
        if (value.toString() != otherValue.toString()) {
            throw RuntimeException("Attempting to set global property $propertyName to two different values ($value vs $otherValue)")
        }
    }
    extra.set(propertyName, value)
}

fun isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
    || gradle.startParameter.taskNames.any { it.contains(":$taskName") }
