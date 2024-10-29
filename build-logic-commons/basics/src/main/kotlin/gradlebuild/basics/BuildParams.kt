/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.basics

import gradlebuild.basics.BuildParams.AUTO_DOWNLOAD_ANDROID_STUDIO
import gradlebuild.basics.BuildParams.BUILD_BRANCH
import gradlebuild.basics.BuildParams.BUILD_COMMIT_DISTRIBUTION
import gradlebuild.basics.BuildParams.BUILD_COMMIT_ID
import gradlebuild.basics.BuildParams.BUILD_FINAL_RELEASE
import gradlebuild.basics.BuildParams.BUILD_ID
import gradlebuild.basics.BuildParams.BUILD_IGNORE_INCOMING_BUILD_RECEIPT
import gradlebuild.basics.BuildParams.BUILD_MILESTONE_NUMBER
import gradlebuild.basics.BuildParams.BUILD_PROMOTION_COMMIT_ID
import gradlebuild.basics.BuildParams.BUILD_RC_NUMBER
import gradlebuild.basics.BuildParams.BUILD_TIMESTAMP
import gradlebuild.basics.BuildParams.BUILD_VCS_NUMBER
import gradlebuild.basics.BuildParams.BUILD_VERSION_QUALIFIER
import gradlebuild.basics.BuildParams.BUNDLE_GROOVY_4
import gradlebuild.basics.BuildParams.CI_ENVIRONMENT_VARIABLE
import gradlebuild.basics.BuildParams.DEFAULT_PERFORMANCE_BASELINES
import gradlebuild.basics.BuildParams.ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS
import gradlebuild.basics.BuildParams.FLAKY_TEST
import gradlebuild.basics.BuildParams.GRADLE_INSTALL_PATH
import gradlebuild.basics.BuildParams.INCLUDE_PERFORMANCE_TEST_SCENARIOS
import gradlebuild.basics.BuildParams.MAX_PARALLEL_FORKS
import gradlebuild.basics.BuildParams.MAX_TEST_DISTRIBUTION_LOCAL_EXECUTORS
import gradlebuild.basics.BuildParams.MAX_TEST_DISTRIBUTION_REMOTE_EXECUTORS
import gradlebuild.basics.BuildParams.PERFORMANCE_BASELINES
import gradlebuild.basics.BuildParams.PERFORMANCE_CHANNEL_ENV
import gradlebuild.basics.BuildParams.PERFORMANCE_DB_PASSWORD
import gradlebuild.basics.BuildParams.PERFORMANCE_DB_PASSWORD_ENV
import gradlebuild.basics.BuildParams.PERFORMANCE_DB_URL
import gradlebuild.basics.BuildParams.PERFORMANCE_DB_USERNAME
import gradlebuild.basics.BuildParams.PERFORMANCE_DEPENDENCY_BUILD_IDS
import gradlebuild.basics.BuildParams.PERFORMANCE_MAX_PROJECTS
import gradlebuild.basics.BuildParams.PERFORMANCE_TEST_VERBOSE
import gradlebuild.basics.BuildParams.PREDICTIVE_TEST_SELECTION_ENABLED
import gradlebuild.basics.BuildParams.RERUN_ALL_TESTS
import gradlebuild.basics.BuildParams.RETRY_BUILD
import gradlebuild.basics.BuildParams.RUN_ANDROID_STUDIO_IN_HEADLESS_MODE
import gradlebuild.basics.BuildParams.RUN_BROKEN_CONFIGURATION_CACHE_DOCS_TESTS
import gradlebuild.basics.BuildParams.STUDIO_HOME
import gradlebuild.basics.BuildParams.TEST_DISTRIBUTION_DOGFOODING_TAG
import gradlebuild.basics.BuildParams.TEST_DISTRIBUTION_ENABLED
import gradlebuild.basics.BuildParams.TEST_DISTRIBUTION_PARTITION_SIZE
import gradlebuild.basics.BuildParams.TEST_DISTRIBUTION_SERVER_URL
import gradlebuild.basics.BuildParams.TEST_JAVA_VENDOR
import gradlebuild.basics.BuildParams.TEST_JAVA_VERSION
import gradlebuild.basics.BuildParams.TEST_SPLIT_EXCLUDE_TEST_CLASSES
import gradlebuild.basics.BuildParams.TEST_SPLIT_INCLUDE_TEST_CLASSES
import gradlebuild.basics.BuildParams.TEST_SPLIT_ONLY_TEST_GRADLE_VERSION
import gradlebuild.basics.BuildParams.VENDOR_MAPPING
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly


enum class FlakyTestStrategy {
    INCLUDE, EXCLUDE, ONLY
}


object BuildParams {
    const val BUILD_BRANCH = "BUILD_BRANCH"
    const val BUILD_COMMIT_ID = "BUILD_COMMIT_ID"
    const val BUILD_COMMIT_DISTRIBUTION = "buildCommitDistribution"
    const val BUILD_FINAL_RELEASE = "finalRelease"
    const val BUILD_ID = "BUILD_ID"
    const val BUILD_IGNORE_INCOMING_BUILD_RECEIPT = "ignoreIncomingBuildReceipt"
    const val BUILD_MILESTONE_NUMBER = "milestoneNumber"
    const val BUILD_PROMOTION_COMMIT_ID = "promotionCommitId"
    const val BUILD_RC_NUMBER = "rcNumber"
    const val BUILD_TIMESTAMP = "buildTimestamp"
    const val BUILD_VCS_NUMBER = "BUILD_VCS_NUMBER"
    const val BUILD_VERSION_QUALIFIER = "versionQualifier"
    const val CI_ENVIRONMENT_VARIABLE = "CI"
    const val DEFAULT_PERFORMANCE_BASELINES = "defaultPerformanceBaselines"
    const val GRADLE_INSTALL_PATH = "gradle_installPath"


    /**
     * Specify the flaky test quarantine strategy:
     *
     * -PflakyTests=include: run all tests, including flaky tests.
     * -PflakyTests=exclude: run all tests, excluding flaky tests.
     * -PflakyTests=only: run flaky tests only.
     *
     * Default value (if absent) is "include".
     */
    const val FLAKY_TEST = "flakyTests"
    const val INCLUDE_PERFORMANCE_TEST_SCENARIOS = "includePerformanceTestScenarios"
    const val MAX_PARALLEL_FORKS = "maxParallelForks"
    const val MAX_TEST_DISTRIBUTION_REMOTE_EXECUTORS = "maxTestDistributionRemoteExecutors"
    const val MAX_TEST_DISTRIBUTION_LOCAL_EXECUTORS = "maxTestDistributionLocalExecutors"
    const val PERFORMANCE_BASELINES = "performanceBaselines"
    const val PERFORMANCE_TEST_VERBOSE = "performanceTest.verbose"
    const val PERFORMANCE_DB_PASSWORD = "org.gradle.performance.db.password"
    const val PERFORMANCE_DB_PASSWORD_ENV = "PERFORMANCE_DB_PASSWORD_TCAGENT"
    const val PERFORMANCE_CHANNEL_ENV = "PERFORMANCE_CHANNEL"
    const val PERFORMANCE_DB_URL = "org.gradle.performance.db.url"
    const val PERFORMANCE_DB_USERNAME = "org.gradle.performance.db.username"
    const val PERFORMANCE_DEPENDENCY_BUILD_IDS = "org.gradle.performance.dependencyBuildIds"
    const val PERFORMANCE_MAX_PROJECTS = "maxProjects"
    const val RERUN_ALL_TESTS = "rerunAllTests"
    const val RETRY_BUILD = "retryBuild"
    const val PREDICTIVE_TEST_SELECTION_ENABLED = "enablePredictiveTestSelection"
    const val TEST_DISTRIBUTION_DOGFOODING_TAG = "testDistributionDogfoodingTag"
    const val TEST_DISTRIBUTION_ENABLED = "enableTestDistribution"
    const val TEST_DISTRIBUTION_PARTITION_SIZE = "testDistributionPartitionSizeInSeconds"
    const val TEST_DISTRIBUTION_SERVER_URL = "testDistributionServerUrl"
    const val TEST_SPLIT_INCLUDE_TEST_CLASSES = "includeTestClasses"
    const val TEST_SPLIT_EXCLUDE_TEST_CLASSES = "excludeTestClasses"
    const val TEST_SPLIT_ONLY_TEST_GRADLE_VERSION = "onlyTestGradleVersion"
    const val TEST_JAVA_VENDOR = "testJavaVendor"
    const val TEST_JAVA_VERSION = "testJavaVersion"
    const val AUTO_DOWNLOAD_ANDROID_STUDIO = "autoDownloadAndroidStudio"
    const val RUN_ANDROID_STUDIO_IN_HEADLESS_MODE = "runAndroidStudioInHeadlessMode"
    const val STUDIO_HOME = "studioHome"
    const val BUNDLE_GROOVY_4 = "bundleGroovy4"

    /**
     * Run docs tests with the configuration cache enabled.
     */
    const val ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS = "enableConfigurationCacheForDocsTests"

    /**
     * Run docs tests that are knowingly broken when running with the configuration cache enabled. Only applied when #ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS is also set.
     */
    const val RUN_BROKEN_CONFIGURATION_CACHE_DOCS_TESTS = "runBrokenConfigurationCacheDocsTests"

    internal
    val VENDOR_MAPPING = mapOf(
        "oracle" to JvmVendorSpec.ORACLE,
        "openjdk" to JvmVendorSpec.ADOPTIUM,
        "zulu" to JvmVendorSpec.AZUL
    )
}


fun Project.stringPropertyOrEmpty(projectPropertyName: String): String =
    stringPropertyOrNull(projectPropertyName) ?: ""


fun Project.stringPropertyOrNull(projectPropertyName: String): String? =
    gradleProperty(projectPropertyName).orNull


fun Project.selectStringProperties(vararg propertyNames: String): Map<String, String> =
    propertyNames.mapNotNull { propertyName ->
        stringPropertyOrNull(propertyName)?.let { propertyValue ->
            propertyName to propertyValue
        }
    }.toMap()


/**
 * Creates a [Provider] that returns `true` when this [Provider] has a value
 * and `false` otherwise. The returned [Provider] always has a value.
 * @see Provider.isPresent
 */
private
fun <T> Provider<T>.presence(): Provider<Boolean> =
    map { true }.orElse(false)


fun Project.gradleProperty(propertyName: String) = providers.gradleProperty(propertyName)


fun Project.systemProperty(propertyName: String) = providers.systemProperty(propertyName)


fun Project.environmentVariable(propertyName: String) = providers.environmentVariable(propertyName)


fun Project.propertyFromAnySource(propertyName: String) = gradleProperty(propertyName)
    .orElse(systemProperty(propertyName))
    .orElse(environmentVariable(propertyName))


val Project.buildBranch: Provider<String>
    get() = environmentVariable(BUILD_BRANCH).orElse(currentGitBranchViaFileSystemQuery())


/**
 * The logical branch.
 * For non-merge-queue branches this is the same as {@link #buildBranch}.
 * For merge-queue branches, this is the base branch.
 *
 * For example, for the merge queue branch "gh-readonly-queue/master/pr-12345-1a2b3c4d" the logical branch is "master".
 */
val Project.logicalBranch: Provider<String>
    get() = buildBranch.map(::toMergeQueueBaseBranch)


val Project.buildCommitId: Provider<String>
    get() = environmentVariable(BUILD_COMMIT_ID)
        .orElse(gradleProperty(BUILD_PROMOTION_COMMIT_ID))
        .orElse(environmentVariable(BUILD_VCS_NUMBER))
        .orElse(currentGitCommitViaFileSystemQuery())


val Project.isBuildCommitDistribution: Boolean
    get() = gradleProperty(BUILD_COMMIT_DISTRIBUTION).map { it.toBoolean() }.orElse(false).get()


val Project.buildFinalRelease: Provider<String>
    get() = gradleProperty(BUILD_FINAL_RELEASE)


val Project.buildId: Provider<String>
    get() = environmentVariable(BUILD_ID)


val Project.buildRcNumber: Provider<String>
    get() = gradleProperty(BUILD_RC_NUMBER)


val Project.buildRunningOnCi: Provider<Boolean>
    get() = environmentVariable(CI_ENVIRONMENT_VARIABLE).presence()


val Project.buildMilestoneNumber: Provider<String>
    get() = gradleProperty(BUILD_MILESTONE_NUMBER)


val Project.isRetryBuild: Boolean
    get() = gradleProperty(RETRY_BUILD).isPresent


val Project.buildTimestamp: Provider<String>
    get() = gradleProperty(BUILD_TIMESTAMP)


val Project.buildVersionQualifier: Provider<String>
    get() = gradleProperty(BUILD_VERSION_QUALIFIER)


val Project.defaultPerformanceBaselines: Provider<String>
    get() = gradleProperty(DEFAULT_PERFORMANCE_BASELINES)


// null means no limit: use all available executors
val Project.maxTestDistributionRemoteExecutors: Int?
    get() = gradleProperty(MAX_TEST_DISTRIBUTION_REMOTE_EXECUTORS).orNull?.toInt()

val Project.maxTestDistributionLocalExecutors: Int?
    get() = gradleProperty(MAX_TEST_DISTRIBUTION_LOCAL_EXECUTORS).orNull?.toInt()

val Project.toolchainInstallationPaths: Provider<String>
    get() = gradleProperty(JAVA_INSTALLATIONS_PATHS_PROPERTY)

val Project.flakyTestStrategy: FlakyTestStrategy
    get() = gradleProperty(FLAKY_TEST).let {
        if (it.getOrElse("").isEmpty()) {
            return FlakyTestStrategy.INCLUDE
        } else {
            return FlakyTestStrategy.valueOf(it.get().toUpperCaseAsciiOnly())
        }
    }


val Project.ignoreIncomingBuildReceipt: Provider<Boolean>
    get() = gradleProperty(BUILD_IGNORE_INCOMING_BUILD_RECEIPT).presence()


val Project.performanceDependencyBuildIds: Provider<String>
    get() = gradleProperty(PERFORMANCE_DEPENDENCY_BUILD_IDS).orElse("")


val Project.performanceBaselines: String?
    get() = stringPropertyOrNull(PERFORMANCE_BASELINES)

val Project.performanceChannel: Provider<String>
    get() = environmentVariable(PERFORMANCE_CHANNEL_ENV)

val Project.performanceDbPassword: Provider<String>
    get() = environmentVariable(PERFORMANCE_DB_PASSWORD_ENV)


val Project.performanceTestVerbose: Provider<String>
    get() = gradleProperty(PERFORMANCE_TEST_VERBOSE)


val Project.propertiesForPerformanceDb: Map<String, String>
    get() {
        return if (performanceDbPassword.isPresent) {
            selectStringProperties(
                PERFORMANCE_DB_URL,
                PERFORMANCE_DB_USERNAME
            ) + (PERFORMANCE_DB_PASSWORD to performanceDbPassword.get())
        } else {
            selectStringProperties(
                PERFORMANCE_DB_URL,
                PERFORMANCE_DB_USERNAME,
                PERFORMANCE_DB_PASSWORD
            )
        }
    }


val Project.performanceGeneratorMaxProjects: Int?
    get() = gradleProperty(PERFORMANCE_MAX_PROJECTS).map { it.toInt() }.orNull


val Project.includePerformanceTestScenarios: Boolean
    get() = gradleProperty(INCLUDE_PERFORMANCE_TEST_SCENARIOS).getOrElse("false") == "true"


val Project.gradleInstallPath: Provider<String>
    get() = gradleProperty(GRADLE_INSTALL_PATH).orElse(
        provider<String> {
            throw RuntimeException("You can't install without setting the $GRADLE_INSTALL_PATH property.")
        }
    )


val Project.rerunAllTests: Provider<Boolean>
    get() = gradleProperty(RERUN_ALL_TESTS).map { true }.orElse(false)


val Project.testJavaVendor: Provider<JvmVendorSpec>
    get() = propertyFromAnySource(TEST_JAVA_VENDOR).map { vendorName -> VENDOR_MAPPING.getOrElse(vendorName) { -> JvmVendorSpec.matching(vendorName) } }


val Project.testJavaVersion: String
    get() = propertyFromAnySource(TEST_JAVA_VERSION).getOrElse(JavaVersion.current().majorVersion)


val Project.testSplitIncludeTestClasses: String
    get() = project.stringPropertyOrEmpty(TEST_SPLIT_INCLUDE_TEST_CLASSES)


val Project.testSplitExcludeTestClasses: String
    get() = project.stringPropertyOrEmpty(TEST_SPLIT_EXCLUDE_TEST_CLASSES)


val Project.testSplitOnlyTestGradleVersion: String
    get() = project.stringPropertyOrEmpty(TEST_SPLIT_ONLY_TEST_GRADLE_VERSION)


val Project.predictiveTestSelectionEnabled: Provider<Boolean>
    get() = systemProperty(PREDICTIVE_TEST_SELECTION_ENABLED)
        .map { it.toBoolean() }
        .orElse(
            buildBranch.zip(buildRunningOnCi) { branch, ci ->
                val protectedBranches = listOf("master", "release")
                ci && !protectedBranches.contains(branch)
                    && !branch.startsWith("pre-test/")
                    && !branch.startsWith("gh-readonly-queue/")
            }
        ).zip(project.rerunAllTests) { enabled, rerunAllTests ->
            enabled && !rerunAllTests
        }

val Project.testDistributionDogfoodingTag: Provider<String>
    get() = gradleProperty(TEST_DISTRIBUTION_DOGFOODING_TAG)

val Project.testDistributionEnabled: Boolean
    get() = systemProperty(TEST_DISTRIBUTION_ENABLED).orNull?.toBoolean() == true


val Project.testDistributionServerUrl: Provider<String>
    get() = gradleProperty(TEST_DISTRIBUTION_SERVER_URL)


// Controls the test distribution partition size. The test classes smaller than this value will be merged into a "partition"
val Project.maxTestDistributionPartitionSecond: Long?
    get() = systemProperty(TEST_DISTRIBUTION_PARTITION_SIZE).orNull?.toLong()


val Project.maxParallelForks: Int
    get() = gradleProperty(MAX_PARALLEL_FORKS).getOrElse("4").toInt()


val Project.autoDownloadAndroidStudio: Boolean
    get() = propertyFromAnySource(AUTO_DOWNLOAD_ANDROID_STUDIO).getOrElse("false").toBoolean()


val Project.runAndroidStudioInHeadlessMode: Boolean
    get() = propertyFromAnySource(RUN_ANDROID_STUDIO_IN_HEADLESS_MODE).getOrElse("false").toBoolean()


val Project.androidStudioHome: Provider<String>
    get() = propertyFromAnySource(STUDIO_HOME)


/**
 * If set to `true`, run docs tests with the configuration cache enabled.
 */
val Project.configurationCacheEnabledForDocsTests: Boolean
    @JvmName("isConfigurationCacheEnabledForDocsTests") get() = gradleProperty(ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS).orNull.toBoolean()


/**
 * If set to `true`, run docs tests that are knowingly broken when running with the configuration cache enabled. Only applied when #configurationCacheEnabledForDocsTests is also set.
 */
val Project.runBrokenForConfigurationCacheDocsTests: Boolean
    @JvmName("shouldRunBrokenForConfigurationCacheDocsTests") get() = gradleProperty(RUN_BROKEN_CONFIGURATION_CACHE_DOCS_TESTS).orNull.toBoolean()


/**
 * Is a promotion build task called?
 */
val Project.isPromotionBuild: Boolean
    get() {
        val taskNames = gradle.startParameter.taskNames
        return taskNames.contains("promotionBuild") ||
            // :updateReleasedVersionsToLatestNightly and :updateReleasedVersions
            taskNames.any { it.contains("updateReleasedVersions") }
    }


/**
 * If `-DbundleGroovy4=true` is specified, create a distribution using Groovy 4 libs.  Otherwise use Groovy 3 classic libs.
 */
val Project.isBundleGroovy4: Boolean
    get() = systemProperty(BUNDLE_GROOVY_4).orNull.toBoolean()
