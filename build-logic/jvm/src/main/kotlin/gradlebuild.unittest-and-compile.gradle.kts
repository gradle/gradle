/*
 * Copyright 2018 the original author or authors.
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

import com.gradle.develocity.agent.gradle.internal.test.PredictiveTestSelectionConfigurationInternal
import com.gradle.develocity.agent.gradle.internal.test.TestDistributionConfigurationInternal
import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.FlakyTestStrategy
import gradlebuild.basics.accessors.kotlinMainSourceSet
import gradlebuild.basics.buildRunningOnCi
import gradlebuild.basics.flakyTestStrategy
import gradlebuild.basics.maxParallelForks
import gradlebuild.basics.maxTestDistributionLocalExecutors
import gradlebuild.basics.maxTestDistributionPartitionSecond
import gradlebuild.basics.maxTestDistributionRemoteExecutors
import gradlebuild.basics.predictiveTestSelectionEnabled
import gradlebuild.basics.rerunAllTests
import gradlebuild.basics.testDistributionDogfoodingTag
import gradlebuild.basics.testDistributionEnabled
import gradlebuild.basics.testDistributionServerUrl
import gradlebuild.basics.testJavaVendor
import gradlebuild.basics.testJavaVersion
import gradlebuild.basics.testing.excludeSpockAnnotation
import gradlebuild.basics.testing.includeSpockAnnotation
import gradlebuild.filterEnvironmentVariables
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.extension.ModuleTargetRuntimes
import gradlebuild.jvm.argumentproviders.CiEnvironmentProvider
import gradlebuild.jvm.extension.UnitTestAndCompileExtension
import org.gradle.internal.jvm.JpmsConfiguration
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration
import java.util.Optional

plugins {
    groovy
    idea // Need to apply the idea plugin, so the extended configuration is taken into account on sync
    id("gradlebuild.module-jar")
    id("gradlebuild.dependency-modules")
}

val gradleModule = the<GradleModuleExtension>()

// Create an extension that allows projects to configure the way they are compiled and tested.
//
// Projects may describe whether they are using any "workarounds" like:
// - Using JDK internal classes
// - Using Java standard library APIs that were introduced after the JVM version they are targeting
// - Using dependencies that target a higher JVM version than the project's target JVM version
//
// All of these workarounds should be generally avoided, but, with this data we can configure the
// compile tasks to permit some of these requirements.
val jvmCompile = extensions.create<UnitTestAndCompileExtension>(UnitTestAndCompileExtension.NAME).apply {
    // And assume it does not use any workarounds
    usesJdkInternals = false
    usesFutureStdlib = false

    // By default, use the target runtimes of the module to determine the java version
    targetJvmVersion = gradleModule.targetRuntimes.computeProductionJvmTargetVersion()
}

enforceCompatibility(jvmCompile)

removeTeamcityTempProperty()
addDependencies()
configureCompileDefaults()
addCompileAllTasks()
configureSourcesVariant()
configureTests()

tasks.registerCITestDistributionLifecycleTasks()

fun configureCompileDefaults() {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
        configureCompileTask(options)
    }
    tasks.withType<GroovyCompile>().configureEach {
        groovyOptions.encoding = "utf-8"
        configureCompileTask(options)
    }
}

/**
 * Given the user-configured values in the extension, configure the compilation tasks
 * to enforce compatibility with the target JVM version.
 *
 * We try to use the toolchain configured in [configureDefaultToolchain] as much as possible,
 * but in some cases, we need to use another toolchain.
 *
 * In some cases, we need to set the source and target compatibility flags instead of using
 * the release flag. This is because the release flag limits us from using internal APIs or
 * APIs defined by a Java version higher than the target version.
 *
 * Finally, Groovy does not support the release flag at all. We manually set a toolchain
 * for Groovy to ensure it compiles against the correct classes.
 */
private
fun enforceCompatibility(jvmCompile: UnitTestAndCompileExtension) {
    // When using the release flag, compiled code cannot access JDK internal classes or standard library
    // APIs defined in future versions of Java. If either of these cases are true, we do not use the
    // release flag, but instead set the source and target compatibility flags.
    val useRelease = jvmCompile.usesJdkInternals.zip(jvmCompile.usesFutureStdlib) { internals, futureApis -> !internals && !futureApis }

    enforceJavaCompatibility(jvmCompile.targetJvmVersion, useRelease)
    enforceGroovyCompatibility(jvmCompile.targetJvmVersion)
    enforceKotlinCompatibility(jvmCompile.targetJvmVersion, useRelease)
}

/**
 * Given the declared target platforms of a given Gradle module, determine
 * the JVM version that the production code should target.
 */
fun ModuleTargetRuntimes.computeProductionJvmTargetVersion(): Provider<Int> {
    // Should be kept in sync with org.gradle.internal.jvm.SupportedJavaVersions
    val targetRuntimeJavaVersions = mapOf(
        usedInWorkers to 8,
        usedInClient to 8,
        usedInDaemon to 8
    )

    return reduceBooleanFlagValues(targetRuntimeJavaVersions, ::minOf).orElse(provider {
        throw GradleException("No target JVM version configured. Specify a runtime target for $project on ${UnitTestAndCompileExtension::class.java.simpleName} for $project")
    })
}

fun enforceJavaCompatibility(targetVersion: Provider<Int>, useRelease: Provider<Boolean>) {
    tasks.withType<JavaCompile>().configureEach {
        // Set the release flag is requested.
        // Otherwise, we set the source and target compatibility in the afterEvaluate below.
        options.release = useRelease.zip(targetVersion) { doUseRelease, target -> if (doUseRelease) { target } else { null } }
    }

    // Need to use afterEvaluate since source/target compatibility are not lazy
    project.afterEvaluate {
        tasks.withType<JavaCompile>().configureEach {
            if (!useRelease.get()) {
                val version = targetVersion.get().toString()
                sourceCompatibility = version
                targetCompatibility = version
            }
        }
    }
}

fun enforceGroovyCompatibility(targetVersion: Provider<Int>) {
    tasks.withType<GroovyCompile>().configureEach {
        // Groovy does not support the release flag. We must compile with the same
        // JDK we are targeting in order to see the correct standard lib classes
        // during compilation
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = targetVersion.map { JavaLanguageVersion.of(it) }
        }
    }

    // Need to use afterEvaluate since source/target Compatibility are not lazy
    project.afterEvaluate {
        tasks.withType<GroovyCompile>().configureEach {
            val version = targetVersion.get().toString()
            sourceCompatibility = version
            targetCompatibility = version
        }
    }
}

fun enforceKotlinCompatibility(targetVersion: Provider<Int>, useRelease: Provider<Boolean>) {
    tasks.withType<KotlinCompile>().configureEach {
        jvmTargetValidationMode.set(JvmTargetValidationMode.ERROR)
        compilerOptions {
            jvmTarget = targetVersion.map {
                JvmTarget.fromTarget(if (it < 9) "1.${it}" else it.toString())
            }

            // TODO KT-49746: Use the DSL to set the release version
            freeCompilerArgs.addAll(useRelease.zip(jvmTarget) { doUseRelease, targetVersion ->
                if (doUseRelease) {
                    listOf("-Xjdk-release=${targetVersion.target}")
                } else {
                    listOf()
                }
            })
        }
    }
}

fun configureSourcesVariant() {
    java {
        withSourcesJar()
    }

    @Suppress("UnusedPrivateProperty")
    val transitiveSourcesElements by configurations.creating {
        isCanBeResolved = false
        isCanBeConsumed = true
        extendsFrom(configurations.implementation.get())
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-source-folders"))
        }
        val main = sourceSets.main.get()
        main.java.srcDirs.forEach {
            outgoing.artifact(it)
        }
        main.groovy.srcDirs.forEach {
            outgoing.artifact(it)
        }
        main.resources.srcDirs.forEach {
            outgoing.artifact(it)
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            kotlinMainSourceSet.srcDirs.forEach {
                outgoing.artifact(it)
            }
        }
    }
}

fun configureCompileTask(options: CompileOptions) {
    options.encoding = "utf-8"
    options.isIncremental = true
    options.isFork = true
    options.forkOptions.jvmArgs?.add("-XX:+HeapDumpOnOutOfMemoryError")
    options.forkOptions.memoryMaximumSize = "1g"
    options.compilerArgs.addAll(mutableListOf("-Xlint:-options", "-Xlint:-path"))
}

fun addDependencies() {
    dependencies {
        testCompileOnly(libs.junit)
        testRuntimeOnly(libs.junit5Vintage)
        testImplementation(libs.groovy)
        testImplementation(libs.groovyAnt)
        testImplementation(libs.groovyJson)
        testImplementation(libs.groovyTest)
        testImplementation(libs.groovyXml)
        testImplementation(libs.spock)
        testImplementation(libs.junit5Vintage)
        testImplementation(libs.spockJUnit4)
        testImplementation(libs.develocityTestAnnotation)
        testRuntimeOnly(libs.bytebuddy)
        testRuntimeOnly(libs.objenesis)
        testRuntimeOnly(libs.junitPlatform)

        // use a separate configuration for the platform dependency that does not get published as part of 'apiElements' or 'runtimeElements'
        val platformImplementation by configurations.creating
        configurations["compileClasspath"].extendsFrom(platformImplementation)
        configurations["runtimeClasspath"].extendsFrom(platformImplementation)
        configurations["testCompileClasspath"].extendsFrom(platformImplementation)
        configurations["testRuntimeClasspath"].extendsFrom(platformImplementation)
        // use lazy API to not attempt to find platform project during script compilation
        platformImplementation.dependencies.addLater(provider {
            project.dependencies.platform(project.dependencies.create(project(":distributions-dependencies")))
        })
    }
}

fun addCompileAllTasks() {
    tasks.register("compileAll") {
        description = "Compile all source code, including main, test, integTest, crossVersionTest, testFixtures, etc."
        val compileTasks = project.tasks.matching {
            it is JavaCompile || it is GroovyCompile
        }
        dependsOn(compileTasks)
    }

    tasks.register("compileAllProduction") {
        description = "Compile all production source code, usually only main and testFixtures."
        val compileTasks = project.tasks.matching {
            // Currently, we compile everything since the Groovy compiler is not deterministic enough.
            (it is JavaCompile || it is GroovyCompile)
        }
        dependsOn(compileTasks)
    }
}

fun Test.configureSpock() {
    systemProperty("spock.configuration", "GradleBuildSpockConfig.groovy")
}

fun Test.configureFlakyTest() {
    when (project.flakyTestStrategy) {
        FlakyTestStrategy.INCLUDE -> {}
        FlakyTestStrategy.EXCLUDE -> {
            excludeSpockAnnotation("org.gradle.test.fixtures.Flaky")
            (options as JUnitPlatformOptions).excludeTags("org.gradle.test.fixtures.Flaky")
        }

        FlakyTestStrategy.ONLY -> {
            // Note there is an issue: https://github.com/spockframework/spock/issues/1288
            // JUnit Platform `includeTags` works before Spock engine, thus excludes all spock tests.
            // As a workaround, we tag all non-spock integration tests and use `includeTags(none() | Flaky)` here.
            (options as JUnitPlatformOptions).includeTags("none() | org.gradle.test.fixtures.Flaky")
            includeSpockAnnotation("org.gradle.test.fixtures.Flaky")
        }
    }
}

fun Test.runWithJavaVersion(testJvmVersion: JavaLanguageVersion) {
    javaLauncher = project.javaToolchains.launcherFor {
        languageVersion = testJvmVersion
        if (project.testJavaVendor.isPresent) {
            vendor = project.testJavaVendor
        }
    }

    if (testJvmVersion.canCompileOrRun(9)) {
        val argProvider = objects.newInstance(AddOpensArgumentProvider::class.java).apply {
            jvmVersion = testJvmVersion.asInt()
            unitTest = provider { isUnitTest() }
            embedded = provider { usesEmbeddedExecuter() }
        }
        jvmArgumentProviders.add(argProvider)
    }
}

internal
abstract class AddOpensArgumentProvider : CommandLineArgumentProvider {
    @get:Input
    abstract val jvmVersion: Property<Int>

    @get:Input
    abstract val unitTest: Property<Boolean>

    @get:Input
    abstract val embedded: Property<Boolean>

    override fun asArguments(): Iterable<String> {
        return if (unitTest.get() || embedded.get()) {
            JpmsConfiguration.forDaemonProcesses(jvmVersion.get().toInt(), true)
        } else {
            listOf("--add-opens", "java.base/java.util=ALL-UNNAMED") + // Used in tests by native platform library: WrapperProcess.getEnv
                listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")   // Used in tests by ClassLoaderUtils
        }
    }
}

fun Test.addOsAsInputs() {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
}

fun Test.isUnitTest() = listOf("test", "writePerformanceScenarioDefinitions", "writeTmpPerformanceScenarioDefinitions").contains(name)

fun Test.usesEmbeddedExecuter() = systemProperties["org.gradle.integtest.executer"]?.equals("embedded") ?: false

fun Test.configureRerun() {
    if (project.rerunAllTests.get()) {
        doNotTrackState("All tests should re-run")
    }
}

fun Test.determineMaxRetries() = when {
    project.flakyTestStrategy == FlakyTestStrategy.ONLY -> 4
    else -> 2
}

fun Test.determineMaxFailures() = when {
    project.flakyTestStrategy == FlakyTestStrategy.ONLY -> Integer.MAX_VALUE
    else -> 10
}

fun configureTests() {
    normalization {
        runtimeClasspath {
            // Ignore the build receipt as it is not relevant for tests and changes between each execution
            ignore("org/gradle/build-receipt.properties")
        }
    }

    tasks.withType<Test>().configureEach {

        configureAndroidUserHome()
        filterEnvironmentVariables()

        maxParallelForks = project.maxParallelForks

        jvmArgumentProviders.add(CiEnvironmentProvider(this))
        runWithJavaVersion(JavaLanguageVersion.of(project.testJavaVersion))

        if (name != "archTest") {
            // TODO distinguish archTest and other tests
            addOsAsInputs()
        }
        configureRerun()

        if (BuildEnvironment.isCiServer) {
            develocity.testRetry {
                maxRetries.convention(determineMaxRetries())
                maxFailures = determineMaxFailures()
            }
        }

        useJUnitPlatform()
        configureSpock()
        configureFlakyTest()

        extensions.findByType<DevelocityTestConfiguration>()?.testDistribution {
            this as TestDistributionConfigurationInternal
            server = uri(testDistributionServerUrl.orElse("https://gbt-td.grdev.net"))

            if (project.testDistributionEnabled && !isUnitTest() && !isPerformanceProject() && !isNativeProject() && !isKotlinDslToolingBuilders()) {
                enabled = true
                project.maxTestDistributionPartitionSecond?.apply {
                    preferredMaxDuration = Duration.ofSeconds(this)
                }
                maxRemoteExecutors = if (project.isPerformanceProject()) 0 else project.maxTestDistributionRemoteExecutors
                maxLocalExecutors = project.maxTestDistributionLocalExecutors

                if (maxLocalExecutors.orNull != 0) {
                    localOnly {
                        includeAnnotationClasses.addAll("org.gradle.testdistribution.LocalOnly")
                    }
                }

                val dogfoodingTag = testDistributionDogfoodingTag.getOrElse("gbt-dogfooding")
                if (BuildEnvironment.isCiServer) {
                    when {
                        OperatingSystem.current().isLinux -> requirements = listOf("os=linux", dogfoodingTag)
                        OperatingSystem.current().isWindows -> requirements = listOf("os=windows", dogfoodingTag)
                        OperatingSystem.current().isMacOsX -> requirements = listOf("os=macos", dogfoodingTag)
                    }
                } else {
                    requirements = listOf(dogfoodingTag)
                }
            }
        }

        if (project.supportsPredictiveTestSelection() && !isUnitTest()) {
            // GitHub actions for contributor PRs uses public build scan instance
            // in this case we need to explicitly configure the PTS server
            // Don't move this line into the lambda as it may cause config cache problems
            extensions.findByType<DevelocityTestConfiguration>()?.predictiveTestSelection {
                this as PredictiveTestSelectionConfigurationInternal
                server = uri("https://ge.gradle.org")
                enabled.convention(project.predictiveTestSelectionEnabled)
            }
        }
    }
}

fun removeTeamcityTempProperty() {
    // Undo: https://github.com/JetBrains/teamcity-gradle/blob/e1dc98db0505748df7bea2e61b5ee3a3ba9933db/gradle-runner-agent/src/main/scripts/init.gradle#L818
    if (project.buildRunningOnCi.get() && project.hasProperty("teamcity")) {
        @Suppress("UNCHECKED_CAST") val teamcity = project.property("teamcity") as MutableMap<String, Any>
        teamcity["teamcity.build.tempDir"] = ""
    }
}

fun Project.isPerformanceProject() = setOf("build-scan-performance", "performance").contains(name)

fun Project.isNativeProject() = name.contains("native")

fun Project.isKotlinDslToolingBuilders() = name.contains("kotlin-dsl-tooling-builders")

/**
 * Whether the project supports running with predictive test selection.
 *
 * Our performance tests don't work with PTS, yet.
 * Smoke and soak tests are hard to grasp for PTS, that is why we run them without.
 * When running on Windows with PTS, SimplifiedKotlinScriptEvaluatorTest fails. See https://github.com/gradle/gradle-private/issues/3615.
 */
fun Project.supportsPredictiveTestSelection() = !isPerformanceProject() && !setOf("smoke-test", "soak", "kotlin-dsl", "smoke-ide-test").contains(name)

/**
 * Test lifecycle tasks that correspond to CIBuildModel.TestType (see .teamcity/Gradle_Check/model/CIBuildModel.kt).
 */
fun TaskContainer.registerCITestDistributionLifecycleTasks() {
    val ciGroup = "CI Lifecycle"

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

    register("forceRealizeDependencyManagementTest") {
        description = "Runs all integration tests with the dependency management engine in 'force component realization' mode"
        group = ciGroup
    }
}

// https://github.com/gradle/gradle-private/issues/3380
fun Test.configureAndroidUserHome() {
    val androidUserHomeForTest = project.layout.buildDirectory.dir("androidUserHomeForTest/$name").get().asFile.absolutePath
    environment["ANDROID_PREFS_ROOT"] = androidUserHomeForTest
    environment["ANDROID_USER_HOME"] = androidUserHomeForTest
}

/**
 * Reduces a map of boolean flags to a single property by applying the given combiner function
 * to the corresponding values of the properties that are true.
 *
 * @param flags The map of boolean properties to their values.
 * @param combiner The function to combine the values of the true properties.
 *
 * @return A property that contains the reduced value.
 */
fun <T: Any> reduceBooleanFlagValues(flags: Map<Property<Boolean>, T>, combiner: (T, T) -> T): Provider<T> {
    return flags.entries
        .map { entry ->
            entry.key.map {
                when (it) {
                    true -> Optional.of(entry.value)
                    false -> Optional.empty()
                }
            }.orElse(provider {
                throw GradleException("Expected boolean flag to be configured")
            })
        }
        .reduce { acc, next ->
            acc.zip(next) { left , right ->
                when {
                    !left.isPresent -> right
                    !right.isPresent -> left
                    else -> Optional.of(combiner(left.get(), right.get()))
                }
            }
        }
        .map { it.orElse(null) }
}
