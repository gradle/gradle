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

plugins {
    id("lifecycle-base") // Make sure the basic lifecycle tasks are added through 'lifecycle-base'. Other plugins might sneakily apply that to the root project.
}

val ciGroup = "CI Lifecycle"

val baseLifecycleTasks = listOf(
    LifecycleBasePlugin.CLEAN_TASK_NAME,
    LifecycleBasePlugin.ASSEMBLE_TASK_NAME,
    LifecycleBasePlugin.BUILD_TASK_NAME
)

// See also 'gradlebuild.ci-lifecycle'
val lifecycleTasks = mapOf(
    "compileAllBuild" to "Initialize CI Pipeline by priming the cache before fanning out",
    "sanityCheck" to "Run all basic checks (without tests) - to be run locally and on CI for early feedback",
    "unitTest" to "Run only unitTests (usually the default 'test' task of each project)",
    "quickTest" to "Run all unit, integration and cross-version (against latest release) tests in embedded execution mode",
    "platformTest" to "Run all unit, integration and cross-version (against latest release) tests in forking execution mode",
    "allVersionsIntegMultiVersionTest" to "Run all multi-version integration tests with all version to cover",
    "parallelTest" to "Run all integration tests in parallel execution mode: each Gradle execution started in a test run with --parallel",
    "noDaemonTest" to "Run all integration tests in no-daemon execution mode: each Gradle execution started in a test forks a new daemon",
    "configCacheTest" to "Run all integration tests with instant execution",
    "watchFsTest" to "Run all integration tests with file system watching enabled",
    "forceRealizeDependencyManagementTest" to "Runs all integration tests with the dependency management engine in 'force component realization' mode",
    "quickFeedbackCrossVersionTest" to "Run cross-version tests against a limited set of versions",
    "allVersionsCrossVersionTest" to "Run cross-version tests against all released versions (latest patch release of each)"
)

if (subprojects.isEmpty() && gradle.parent == null) { // the umbrella build if any
    baseLifecycleTasks.forEach { lifecycleTask ->
        tasks.named(lifecycleTask) {
            group = ciGroup
            dependsOn(gradle.includedBuilds.filter { !it.name.contains("build-logic") }.map { it.task(":$lifecycleTask") })
        }
    }
    lifecycleTasks.forEach { (lifecycleTask, taskDescription) ->
        tasks.register(lifecycleTask) {
            group = ciGroup
            description = taskDescription
            dependsOn(gradle.includedBuilds.filter { !it.name.contains("build-logic") }.map { it.task(":$lifecycleTask") })
        }
    }
    tasks.registerDistributionsPromotionTasks()
    tasks.expandSanityCheck()
} else if (subprojects.isNotEmpty()) { // a root build
    setupGlobalState()
    baseLifecycleTasks.forEach { lifecycleTask ->
        tasks.named(lifecycleTask) {
            group = ciGroup
            dependsOn(subprojects.map { "${it.name}:$lifecycleTask" })
        }
    }
    lifecycleTasks.forEach { (lifecycleTask, taskDescription) ->
        tasks.register(lifecycleTask) {
            group = ciGroup
            description = taskDescription
            dependsOn(subprojects.map { "${it.name}:$lifecycleTask" })
        }
    }
} else {
    lifecycleTasks.forEach { (lifecycleTask, taskDescription) ->
        tasks.register(lifecycleTask) {
            group = ciGroup
            description = taskDescription
        }
    }
}

/**
 * Task that are called by the (currently separate) promotion build running on CI.
 */
fun TaskContainer.registerDistributionsPromotionTasks() {
    register("packageBuild") {
        description = "Build production distros and smoke test them"
        group = "build"
        dependsOn(
            gradle.includedBuild("subprojects").task(":distributions-full:verifyIsProductionBuildEnvironment"),
            gradle.includedBuild("subprojects").task(":distributions-full:buildDists"),
            gradle.includedBuild("subprojects").task(":distributions-integ-tests:forkingIntegTest"),
            gradle.includedBuild("subprojects").task(":docs:releaseNotes"),
            gradle.includedBuild("subprojects").task(":docs:incubationReport"),
            gradle.includedBuild("subprojects").task(":docs:checkDeadInternalLinks")
        )
    }
}

fun TaskContainer.expandSanityCheck() {
    named("sanityCheck") {
        dependsOn(
            gradle.includedBuild("build-logic-commons").task(":check"),
            gradle.includedBuild("build-logic").task(":check"),
            gradle.includedBuild("subprojects").task(":docs:checkstyleApi"),
            gradle.includedBuild("subprojects").task(":internal-build-reports:allIncubationReportsZip"),
            gradle.includedBuild("subprojects").task(":architecture-test:checkBinaryCompatibility"),
            gradle.includedBuild("subprojects").task(":docs:javadocAll"),
            gradle.includedBuild("subprojects").task(":architecture-test:test"),
            gradle.includedBuild("subprojects").task(":tooling-api:toolingApiShadedJar"),
            gradle.includedBuild("subprojects").task(":performance:verifyPerformanceScenarioDefinitions"),
            ":checkSubprojectsInfo"
        )
    }
}

fun setupGlobalState() {
    if (needsToUseTestVersionsPartial()) {
        globalProperty("testVersions" to "partial")
    }
    if (needsToUseTestVersionsAll()) {
        globalProperty("testVersions" to "all")
    }
}

fun needsToUseTestVersionsPartial() = isRequestedTask("platformTest")

fun needsToUseTestVersionsAll() = isRequestedTask("allVersionsCrossVersionTest")
    || isRequestedTask("allVersionsIntegMultiVersionTest")
    || isRequestedTask("soakTest")

fun isRequestedTask(taskName: String) = gradle.startParameter.taskNames.contains(taskName)
    || gradle.startParameter.taskNames.any { it.contains(":$taskName") }

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
