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

package org.gradle.plugins.lifecycle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.kotlin.dsl.*
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask


/**
 * Lifecycle tasks used to to fan out the build into multiple builds in a CI pipeline.
 */
@Suppress("unused")
class LifecyclePlugin : Plugin<Project> {

    private
    val ciGroup = "CI Lifecycle"

    private
    val compileAllBuild = "compileAllBuild"

    private
    val sanityCheck = "sanityCheck"

    private
    val quickTest = "quickTest"

    private
    val platformTest = "platformTest"

    private
    val quickFeedbackCrossVersionTest = "quickFeedbackCrossVersionTest"

    private
    val allVersionsCrossVersionTest = "allVersionsCrossVersionTest"

    private
    val allVersionsIntegMultiVersionTest = "allVersionsIntegMultiVersionTest"

    private
    val parallelTest = "parallelTest"

    private
    val noDaemonTest = "noDaemonTest"

    private
    val instantTest = "instantTest"

    private
    val watchFsTest = "watchFsTest"

    private
    val soakTest = "soakTest"

    private
    val ignoredSubprojects = listOf(
        "soak", // soak test
        "distributionsIntegTests", // test build distributions
        "architectureTest" // sanity check
    )

    private
    val forceRealizeDependencyManagementTest = "forceRealizeDependencyManagementTest"

    override fun apply(project: Project): Unit = project.run {
        setupTimeoutMonitorOnCI()
        setupGlobalState()
        sharedDependencyAndQualityConfigs()

        subprojects.filter { it.name !in ignoredSubprojects }.forEach { it.registerLifecycleTasks() }

        project(":soak").registerSoakTest()

        tasks.registerDistributionsPromotionTasks()
    }

    private
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
        plugins.withId("gradlebuild.publish-public-libraries") {
            tasks.registerPublishLibrariesPromotionTasks()
        }
    }

    private
    fun Project.registerSoakTest() {
        tasks.register(soakTest) {
            description = "Run all soak tests defined in the :soak subproject"
            group = ciGroup
        }

        tasks.named(soakTest) {
            dependsOn(":soak:embeddedIntegTest")
        }
    }

    /**
     * Print all stacktraces of running JVMs on the machine upon timeout. Helps us diagnose deadlock issues.
     */
    private
    fun Project.setupTimeoutMonitorOnCI() {
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

    private
    fun Project.determineTimeoutMillis() =
        if (isRequestedTask(compileAllBuild) || isRequestedTask(sanityCheck) || isRequestedTask(quickTest)) {
            TimeUnit.MINUTES.toMillis(30)
        } else {
            TimeUnit.MINUTES.toMillis(165) // 2h45m
        }

    private
    fun Project.setupGlobalState() {
        if (needsToIgnoreIncomingBuildReceipt()) {
            globalProperty("ignoreIncomingBuildReceipt" to true)
        }
        if (needsToUseTestVersionsPartial()) {
            globalProperty("testVersions" to "partial")
        }
        if (needsToUseTestVersionsAll()) {
            globalProperty("testVersions" to "all")
        }
    }

    private
    fun Project.sharedDependencyAndQualityConfigs() {
        // TODO remove this cross project configuration by declaring dependency coordinates as fields in a Kotlin object and the versions in the platform directly
        apply(from = "gradle/dependencies.gradle")
        apply(from = "gradle/test-dependencies.gradle")
        apply(from = "gradle/remove-teamcity-temp-property.gradle") // https://github.com/gradle/gradle-private/issues/2463
        allprojects {
            apply(plugin = "gradlebuild.dependencies-metadata-rules")
        }
    }

    private
    fun Project.needsToIgnoreIncomingBuildReceipt() = isRequestedTask(compileAllBuild)

    private
    fun Project.needsToUseTestVersionsPartial() = isRequestedTask(platformTest)

    private
    fun Project.needsToUseTestVersionsAll() = isRequestedTask(allVersionsCrossVersionTest)
        || isRequestedTask(allVersionsIntegMultiVersionTest)
        || isRequestedTask(soakTest)

    /**
     * Basic compile and check lifecycle tasks.
     */
    private
    fun TaskContainer.registerEarlyFeedbackLifecycleTasks() {
        register(compileAllBuild) {
            description = "Initialize CI Pipeline by priming the cache before fanning out"
            group = ciGroup
            dependsOn(":createBuildReceipt", "compileAll")
        }

        register(sanityCheck) {
            description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
            group = "verification"
            dependsOn(
                "compileAll", ":docs:checkstyleApi", "codeQuality", ":internalBuildReports:allIncubationReportsZip",
                ":architectureTest:checkBinaryCompatibility", ":docs:javadocAll",
                ":architectureTest:test", ":toolingApi:toolingApiShadedJar")
        }
    }

    /**
     * Task that are called by the (currently separate) promotion build running on CI.
     */
    private
    fun TaskContainer.registerDistributionsPromotionTasks() {
        register("packageBuild") {
            description = "Build production distros and smoke test them"
            group = "build"
            dependsOn(":distributionsFull:verifyIsProductionBuildEnvironment", ":distributionsFull:buildDists",
                ":distributionsIntegTests:forkingIntegTest", ":docs:releaseNotes", ":docs:incubationReport", ":docs:checkDeadInternalLinks")
        }
    }

    /**
     * Task that are called by the (currently separate) promotion build running on CI.
     */
    private
    fun TaskContainer.registerPublishLibrariesPromotionTasks() {
        register("promotionBuild") {
            description = "Build production distros, smoke test them and publish"
            group = "publishing"
            dependsOn(":distributionsFull:verifyIsProductionBuildEnvironment", ":distributionsFull:buildDists", ":distributionsFull:copyDistributionsToRootBuild",
                ":distributionsIntegTests:forkingIntegTest", ":docs:releaseNotes", "publish", ":docs:incubationReport", ":docs:checkDeadInternalLinks")
        }
    }

    /**
     * Test lifecycle tasks that correspond to CIBuildModel.TestType (see .teamcity/Gradle_Check/model/CIBuildModel.kt).
     */
    private
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

        register(instantTest) {
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

    private
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

        named(instantTest) {
            dependsOn("instantIntegTest")
        }

        named(watchFsTest) {
            dependsOn("watchFsIntegTest")
        }

        named(forceRealizeDependencyManagementTest) {
            dependsOn("integForceRealizeTest")
        }
    }

    private
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

    private
    fun Project.globalProperty(pair: Pair<String, Any>) {
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

    private
    fun Project.isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
        || gradle.startParameter.taskNames.any { it.contains(":$taskName") }
}
