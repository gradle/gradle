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

import com.gradle.enterprise.gradleplugin.testdistribution.TestDistributionExtension
import com.gradle.enterprise.gradleplugin.testdistribution.internal.TestDistributionExtensionInternal
import com.gradle.enterprise.gradleplugin.testretry.retry
import com.gradle.enterprise.gradleplugin.testselection.PredictiveTestSelectionExtension
import com.gradle.enterprise.gradleplugin.testselection.internal.PredictiveTestSelectionExtensionInternal
import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.FlakyTestStrategy
import gradlebuild.basics.accessors.kotlin
import gradlebuild.basics.flakyTestStrategy
import gradlebuild.basics.maxParallelForks
import gradlebuild.basics.maxTestDistributionPartitionSecond
import gradlebuild.basics.predictiveTestSelectionEnabled
import gradlebuild.basics.rerunAllTests
import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.basics.testDistributionEnabled
import gradlebuild.basics.testJavaVendor
import gradlebuild.basics.testJavaVersion
import gradlebuild.basics.testing.excludeSpockAnnotation
import gradlebuild.basics.testing.includeSpockAnnotation
import gradlebuild.filterEnvironmentVariables
import gradlebuild.jvm.argumentproviders.CiEnvironmentProvider
import gradlebuild.jvm.extension.UnitTestAndCompileExtension
import org.gradle.internal.os.OperatingSystem
import java.time.Duration
import java.util.jar.Attributes

plugins {
    groovy
    idea // Need to apply the idea plugin, so the extended configuration is taken into account on sync
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
}

extensions.create<UnitTestAndCompileExtension>("gradlebuildJava", project, tasks)

removeTeamcityTempProperty()
addDependencies()
configureClasspathManifestGeneration()
configureCompile()
configureSourcesVariant()
configureJarTasks()
configureTests()

tasks.registerCITestDistributionLifecycleTasks()

fun configureCompile() {
    java.toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }

    tasks.withType<JavaCompile>().configureEach {
        configureCompileTask(options)
        options.compilerArgs.add("-parameters")
    }
    tasks.withType<GroovyCompile>().configureEach {
        groovyOptions.encoding = "utf-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        configureCompileTask(options)
    }
    addCompileAllTask()
}

fun configureSourcesVariant() {
    java {
        withSourcesJar()
    }

    @Suppress("unused_variable")
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
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            main.kotlin.srcDirs.forEach {
                outgoing.artifact(it)
            }
        }
    }
}

fun configureCompileTask(options: CompileOptions) {
    options.release = 8
    options.encoding = "utf-8"
    options.isIncremental = true
    options.isFork = true
    options.forkOptions.jvmArgs?.add("-XX:+HeapDumpOnOutOfMemoryError")
    options.forkOptions.memoryMaximumSize = "1g"
    options.compilerArgs.addAll(mutableListOf("-Xlint:-options", "-Xlint:-path"))
}

fun configureClasspathManifestGeneration() {
    val runtimeClasspath by configurations
    val classpathManifest = tasks.register("classpathManifest", ClasspathManifest::class) {
        this.runtimeClasspath.from(runtimeClasspath)
        this.externalDependencies.from(runtimeClasspath.fileCollection { it is ExternalDependency })
        this.manifestFile = moduleIdentity.baseName.map { layout.buildDirectory.file("generated-resources/$it-classpath/$it-classpath.properties").get() }
    }
    sourceSets.main.get().output.dir(classpathManifest.map { it.manifestFile.get().asFile.parentFile })
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
        testImplementation(libs.gradleEnterpriseTestAnnotation)
        testRuntimeOnly(libs.bytebuddy)
        testRuntimeOnly(libs.objenesis)

        // use a separate configuration for the platform dependency that does not get published as part of 'apiElements' or 'runtimeElements'
        val platformImplementation by configurations.creating
        configurations["compileClasspath"].extendsFrom(platformImplementation)
        configurations["runtimeClasspath"].extendsFrom(platformImplementation)
        configurations["testCompileClasspath"].extendsFrom(platformImplementation)
        configurations["testRuntimeClasspath"].extendsFrom(platformImplementation)
        platformImplementation.withDependencies {
            // use 'withDependencies' to not attempt to find platform project during script compilation
            add(project.dependencies.create(platform(project(":distributions-dependencies"))))
        }
    }
}

fun addCompileAllTask() {
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

fun configureJarTasks() {
    tasks.withType<Jar>().configureEach {
        archiveBaseName = moduleIdentity.baseName
        archiveVersion = moduleIdentity.version.map { it.baseVersion.version }
        manifest.attributes(mapOf(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle", Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }))
    }
}

fun Test.jvmVersionForTest(): JavaLanguageVersion {
    return JavaLanguageVersion.of(project.testJavaVersion)
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

fun Test.configureJvmForTest() {
    jvmArgumentProviders.add(CiEnvironmentProvider(this))
    val launcher = project.javaToolchains.launcherFor {
        languageVersion = jvmVersionForTest()
        vendor = project.testJavaVendor.orNull
    }
    javaLauncher = launcher
    if (jvmVersionForTest().canCompileOrRun(9)) {
        if (isUnitTest() || usesEmbeddedExecuter()) {
            jvmArgs(org.gradle.internal.jvm.JpmsConfiguration.GRADLE_DAEMON_JPMS_ARGS)
        } else {
            jvmArgs(listOf("--add-opens", "java.base/java.util=ALL-UNNAMED")) // Used in tests by native platform library: WrapperProcess.getEnv
            jvmArgs(listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")) // Used in tests by ClassLoaderUtils
        }
    }
    if (usesEmbeddedExecuter() && OperatingSystem.current().isWindows) {
        // Disable incremental snapshotting for Windows, since it runs OOM,
        // root cause: https://youtrack.jetbrains.com/issue/KT-57757
        jvmArgs("-Dkotlin.incremental.useClasspathSnapshot=false")
    }
}

fun Test.addOsAsInputs() {
    // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
    // the version currently differs between our dev infrastructure, so we only track the name and the architecture
    inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
}

fun Test.isUnitTest() = listOf("test", "writePerformanceScenarioDefinitions", "writeTmpPerformanceScenarioDefinitions").contains(name)

fun Test.usesEmbeddedExecuter() = name.startsWith("embedded")

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

        configureJvmForTest()
        addOsAsInputs()

        if (BuildEnvironment.isCiServer) {
            configureRerun()
            retry {
                maxRetries.convention(determineMaxRetries())
                maxFailures = determineMaxFailures()
            }
            doFirst {
                logger.lifecycle("maxParallelForks for '$path' is $maxParallelForks")
            }
        }

        useJUnitPlatform()
        configureSpock()
        configureFlakyTest()

        extensions.findByType<TestDistributionExtension>()?.apply {
            this as TestDistributionExtensionInternal
            // Dogfooding TD against ge-td-dogfooding in order to test new features and benefit from bug fixes before they are released
            server = uri("https://ge-td-dogfooding.grdev.net")

            if (project.testDistributionEnabled && !isUnitTest() && !isPerformanceProject()) {
                enabled = true
                project.maxTestDistributionPartitionSecond?.apply {
                    preferredMaxDuration = Duration.ofSeconds(this)
                }
                // No limit; use all available executors
                distribution.maxRemoteExecutors = if (project.isPerformanceProject()) 0 else null

                // Test distribution annotation-class filters
                // See: https://docs.gradle.com/enterprise/test-distribution/#gradle_executor_restrictions_class_matcher
                localOnly {
                    includeAnnotationClasses.addAll("com.gradle.enterprise.testing.annotations.LocalOnly")
                }
                remoteOnly {
                    includeAnnotationClasses.addAll("com.gradle.enterprise.testing.annotations.RemoteOnly")
                }

                if (BuildEnvironment.isCiServer) {
                    when {
                        OperatingSystem.current().isLinux -> requirements = listOf("os=linux", "gbt-dogfooding")
                        OperatingSystem.current().isWindows -> requirements = listOf("os=windows", "gbt-dogfooding")
                        OperatingSystem.current().isMacOsX -> requirements = listOf("os=macos", "gbt-dogfooding")
                    }
                } else {
                    requirements = listOf("gbt-dogfooding")
                }
            }
        }

        if (project.supportsPredictiveTestSelection() && !isUnitTest()) {
            // GitHub actions for contributor PRs uses public build scan instance
            // in this case we need to explicitly configure the PTS server
            // Don't move this line into the lambda as it may cause config cache problems
            extensions.findByType<PredictiveTestSelectionExtension>()?.apply {
                this as PredictiveTestSelectionExtensionInternal
                server = uri("https://ge.gradle.org")
                enabled.convention(project.predictiveTestSelectionEnabled)
            }
        }
    }
}

fun removeTeamcityTempProperty() {
    // Undo: https://github.com/JetBrains/teamcity-gradle/blob/e1dc98db0505748df7bea2e61b5ee3a3ba9933db/gradle-runner-agent/src/main/scripts/init.gradle#L818
    if (project.hasProperty("teamcity")) {
        @Suppress("UNCHECKED_CAST") val teamcity = project.property("teamcity") as MutableMap<String, Any>
        teamcity["teamcity.build.tempDir"] = ""
    }
}

fun Project.isPerformanceProject() = setOf("build-scan-performance", "performance").contains(name)

/**
 * Whether the project supports running with predictive test selection.
 *
 * Our performance tests don't work with PTS, yet.
 * Smoke and soak tests are hard to grasp for PTS, that is why we run them without.
 * When running on Windows with PTS, SimplifiedKotlinScriptEvaluatorTest fails. See https://github.com/gradle/gradle-private/issues/3615.
 */
fun Project.supportsPredictiveTestSelection() = !isPerformanceProject() && !setOf("smoke-test", "soak", "kotlin-dsl").contains(name)

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
