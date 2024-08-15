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

package gradlebuild.integrationtests

import gradlebuild.basics.capitalize
import gradlebuild.basics.repoRoot
import gradlebuild.basics.testSplitExcludeTestClasses
import gradlebuild.basics.testSplitIncludeTestClasses
import gradlebuild.basics.testSplitOnlyTestGradleVersion
import gradlebuild.basics.testing.TestType
import gradlebuild.integrationtests.extension.IntegrationTestExtension
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.integrationtests.tasks.IntegrationTest
import gradlebuild.modules.extension.ExternalModulesExtension
import gradlebuild.testing.services.BuildBucketProvider
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.process.CommandLineArgumentProvider


fun Project.addDependenciesAndConfigurations(prefix: String) {
    configurations {
        getByName("${prefix}TestImplementation") { extendsFrom(configurations["testImplementation"]) }
        val platformImplementation = findByName("platformImplementation")

        val distributionRuntimeOnly = bucket("${prefix}TestDistributionRuntimeOnly", "Declare the distribution that is required to run tests")
        val localRepository = bucket("${prefix}TestLocalRepository", "Declare a local repository required as input data for the tests (e.g. :tooling-api)")
        val normalizedDistribution = bucket("${prefix}TestNormalizedDistribution", "Declare a normalized distribution (bin distribution without timestamp in version) to be used in tests")
        val binDistribution = bucket("${prefix}TestBinDistribution", "Declare a bin distribution to be used by tests - useful for testing the final distribution that is published")
        val allDistribution = bucket("${prefix}TestAllDistribution", "Declare a all distribution to be used by tests - useful for testing the final distribution that is published")
        val docsDistribution = bucket("${prefix}TestDocsDistribution", "Declare a docs distribution to be used by tests - useful for testing the final distribution that is published")
        val srcDistribution = bucket("${prefix}TestSrcDistribution", "Declare a src distribution to be used by tests - useful for testing the final distribution that is published")

        getByName("${prefix}TestRuntimeClasspath") {
            extendsFrom(distributionRuntimeOnly)
            if (platformImplementation != null) {
                extendsFrom(platformImplementation)
            }
        }
        if (platformImplementation != null) {
            getByName("${prefix}TestCompileClasspath") {
                extendsFrom(getByName("platformImplementation"))
            }
        }

        resolver("${prefix}TestDistributionRuntimeClasspath", "gradle-bin-installation", distributionRuntimeOnly)
        resolver("${prefix}TestFullDistributionRuntimeClasspath", "gradle-bin-installation")
        resolver("${prefix}TestLocalRepositoryPath", "gradle-local-repository", localRepository)
        resolver("${prefix}TestNormalizedDistributionPath", "gradle-normalized-distribution-zip", normalizedDistribution)
        resolver("${prefix}TestBinDistributionPath", "gradle-bin-distribution-zip", binDistribution)
        resolver("${prefix}TestAllDistributionPath", "gradle-all-distribution-zip", allDistribution)
        resolver("${prefix}TestDocsDistributionPath", "gradle-docs-distribution-zip", docsDistribution)
        resolver("${prefix}TestSrcDistributionPath", "gradle-src-distribution-zip", srcDistribution)
        resolver("${prefix}TestAgentsClasspath", LibraryElements.JAR)
    }

    // do not attempt to find projects when the plugin is applied just to generate accessors
    if (project.name != "gradle-kotlin-dsl-accessors" && project.name != "enterprise-plugin-performance" && project.name != "test" /* remove once wrapper is updated */) {
        dependencies {
            "${prefix}TestImplementation"(project)
            "${prefix}TestRuntimeOnly"(project.the<ExternalModulesExtension>().junit5Vintage)
            "${prefix}TestImplementation"(project(":internal-integ-testing"))
            "${prefix}TestFullDistributionRuntimeClasspath"(project(":distributions-full"))
            // Add the agent JAR to the test runtime classpath so the InProcessGradleExecuter can find the module and spawn daemons.
            // This doesn't apply the agent to the test process.
            "${prefix}TestRuntimeOnly"(project(":instrumentation-agent"))
            "${prefix}TestAgentsClasspath"(project(":instrumentation-agent"))
        }
    }
}


@Suppress("UnusedPrivateProperty")
internal
fun Project.addSourceSet(testType: TestType): SourceSet {
    val prefix = testType.prefix
    val sourceSets = the<SourceSetContainer>()
    val main by sourceSets.getting
    return sourceSets.create("${prefix}Test")
}


internal
fun Project.createTasks(sourceSet: SourceSet, testType: TestType) {
    val prefix = testType.prefix
    val defaultExecuter = "embedded"

    // For all the other executers, add an executer specific task
    testType.executers.forEach { executer ->
        val taskName = "$executer${prefix.capitalize()}Test"
        val testTask = createTestTask(taskName, executer, sourceSet, testType) {}
        if (executer == defaultExecuter) {
            // The test task with the default executer runs with 'check'
            tasks.named("check").configure { dependsOn(testTask) }
        }
    }
    // Create a variant of the test suite to force realization of component metadata
    if (testType == TestType.INTEGRATION) {
        createTestTask(prefix + "ForceRealizeTest", defaultExecuter, sourceSet, testType) {
            systemProperties["org.gradle.integtest.force.realize.metadata"] = "true"
        }
    }
}


fun Project.getBucketProvider() = gradle.sharedServices.registerIfAbsent("buildBucketProvider", BuildBucketProvider::class) {
    parameters.includeTestClasses = project.testSplitIncludeTestClasses
    parameters.excludeTestClasses = project.testSplitExcludeTestClasses
    parameters.onlyTestGradleVersion = project.testSplitOnlyTestGradleVersion
    parameters.repoRoot = repoRoot()
}


internal
abstract class AgentsClasspathProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val agentsClasspath: ConfigurableFileCollection

    override fun asArguments() = agentsClasspath.files.map { "-javaagent:$it" }
}


internal
class SamplesBaseDirPropertyProvider(@InputDirectory @PathSensitive(PathSensitivity.RELATIVE) val autoTestedSamplesDir: Directory) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-DdeclaredSampleInputs=${autoTestedSamplesDir.asFile.absolutePath}")
}


internal
fun Project.createTestTask(name: String, executer: String, sourceSet: SourceSet, testType: TestType, extraConfig: Action<IntegrationTest>): TaskProvider<IntegrationTest> =
    tasks.register<IntegrationTest>(name) {
        val integTest = project.the<IntegrationTestExtension>()
        project.getBucketProvider().get().bucketProvider.configureTest(this, sourceSet.name)
        description = "Runs ${testType.prefix} with $executer executer"
        systemProperties["org.gradle.integtest.executer"] = executer
        addDebugProperties()
        testClassesDirs = sourceSet.output.classesDirs
        classpath = sourceSet.runtimeClasspath
        extraConfig.execute(this)
        if (integTest.usesJavadocCodeSnippets.get()) {
            val samplesDir = layout.projectDirectory.dir("src/main")
            jvmArgumentProviders.add(SamplesBaseDirPropertyProvider(samplesDir))
        }
        setUpAgentIfNeeded(testType, executer)
        disableIfNeeded(project.name, testType, executer)
    }


internal
fun IntegrationTest.disableIfNeeded(projectName: String, testType: TestType, executer: String) {
    if (
        testType == TestType.INTEGRATION &&
        executer == "isolatedProjects" &&
        ignoreWithIsolatedProjectsExecuter.contains(projectName)
    ) {
        isEnabled = false
    }
}


private
fun IntegrationTest.setUpAgentIfNeeded(testType: TestType, executer: String) {
    if (executer == "embedded") {
        // Apply the instrumentation agent to the test process when running integration tests with embedded Gradle executer.
        jvmArgumentProviders.add(project.objects.newInstance<AgentsClasspathProvider>().apply {
            agentsClasspath.from(project.configurations["${testType.prefix}TestAgentsClasspath"])
        })
    }

    val integTestUseAgentSysPropName = "org.gradle.integtest.agent.allowed"
    val integtestAgentAllowed = project.providers.gradleProperty(integTestUseAgentSysPropName);
    if (integtestAgentAllowed.isPresent) {
        val shouldUseAgent = integtestAgentAllowed.get().toBoolean()
        systemProperties[integTestUseAgentSysPropName] = shouldUseAgent.toString()
    }
}


private
fun IntegrationTest.addDebugProperties() {
    // TODO Move magic property out
    val integtestDebug = project.providers.gradleProperty("org.gradle.integtest.debug")
    if (integtestDebug.isPresent) {
        systemProperties["org.gradle.integtest.debug"] = "true"
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    val integtestVerbose = project.providers.gradleProperty("org.gradle.integtest.verbose")
    if (integtestVerbose.isPresent) {
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    val integtestLauncherDebug = project.providers.gradleProperty("org.gradle.integtest.launcher.debug")
    if (integtestLauncherDebug.isPresent) {
        systemProperties["org.gradle.integtest.launcher.debug"] = "true"
    }
}


fun DistributionTest.setSystemPropertiesOfTestJVM(defaultVersions: String) {
    // use -PtestVersions=all or -PtestVersions=1.2,1.3…
    val integTestVersionsSysProp = "org.gradle.integtest.versions"
    val testVersions = project.providers.gradleProperty("testVersions")
    if (testVersions.isPresent) {
        systemProperties[integTestVersionsSysProp] = testVersions.get()
    } else {
        systemProperties[integTestVersionsSysProp] = defaultVersions
    }
}


internal
fun Project.configureIde(testType: TestType) {
    val prefix = testType.prefix
    val sourceSet = the<SourceSetContainer>().getByName("${prefix}Test")

    // We apply lazy as we don't want to depend on the order
    plugins.withType<IdeaPlugin> {
        with(model) {
            module {
                testSources.from(sourceSet.java.srcDirs, sourceSet.the<GroovySourceDirectorySet>().srcDirs)
                testResources.from(sourceSet.resources.srcDirs)
            }
        }
    }
}


private
fun Project.bucket(name: String, description: String) = configurations.create(name) {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = false
    this.description = description
}


private
fun Project.resolver(name: String, libraryElements: String, extends: Configuration? = null) = configurations.create(name) {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(libraryElements))
    }
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
    if (extends != null) {
        extendsFrom(extends)
    }
}

private val ignoreWithIsolatedProjectsExecuter = listOf(
    "antlr",
    "api-metadata",
    "base-asm",
    "base-ide-plugins",
    "base-services",
    "base-services-groovy",
    "bean-serialization-services",
    "build-cache",
    "build-cache-base",
    "build-cache-example-client",
    "build-cache-http",
    "build-cache-local",
    "build-cache-packaging",
    "build-cache-spi",
    "build-configuration",
    "build-events",
    "build-operations",
    "build-option",
    "build-process-services",
    "build-profile",
    "build-state",
    "cli",
    "client-services",
    "code-quality",
    "composite-builds",
    "concurrent",
    "configuration-cache",
    "configuration-cache-base",
    "configuration-problems-base",
    "core-api",
    "core-kotlin-extensions",
    "core-serialization-codecs",
    "daemon-main",
    "daemon-protocol",
    "daemon-server",
    "daemon-services",
    "declarative-dsl-api",
    "declarative-dsl-core",
    "declarative-dsl-evaluator",
    "declarative-dsl-provider",
    "declarative-dsl-tooling-builders",
    "declarative-dsl-tooling-models",
    "dependency-management",
    "dependency-management-serialization-codecs",
    "diagnostics",
    "distributions-basics",
    "distributions-core",
    "distributions-integ-tests",
    "distributions-jvm",
    "distributions-native",
    "distributions-publishing",
    "ear",
    "encryption-services",
    "enterprise-logging",
    "enterprise-operations",
    "enterprise-plugin-performance",
    "enterprise-workers",
    "execution",
    "execution-e2e-tests",
    "file-collections",
    "file-temp",
    "file-watching",
    "files",
    "flow-services",
    "functional",
    "gradle-cli",
    "gradle-cli-main",
    "graph-serialization",
    "guava-serialization-codecs",
    "hashing",
    "ide",
    "ide-native",
    "ide-plugins",
    "input-tracking",
    "installation-beacon",
    "instrumentation-agent",
    "instrumentation-agent-services",
    "instrumentation-declarations",
    "instrumentation-reporting",
    "integ-test",
    "internal-build-reports",
    "internal-instrumentation-api",
    "internal-instrumentation-processor",
    "internal-integ-testing",
    "internal-performance-testing",
    "io",
    "ivy",
    "jacoco",
    "java-compiler-plugin",
    "java-platform",
    "jvm-services",
    "kotlin-dsl",
    "kotlin-dsl-integ-tests",
    "kotlin-dsl-plugins",
    "kotlin-dsl-provider-plugins",
    "kotlin-dsl-tooling-builders",
    "kotlin-dsl-tooling-models",
    "language-groovy",
    "language-java",
    "language-jvm",
    "language-native",
    "launcher",
    "logging",
    "logging-api",
    "maven",
    "messaging",
    "model-core",
    "model-groovy",
    "native",
    "normalization-java",
    "persistent-cache",
    "platform-base",
    "platform-jvm",
    "platform-native",
    "plugin-development",
    "plugin-use",
    "plugins-application",
    "plugins-distribution",
    "plugins-groovy",
    "plugins-java-base",
    "plugins-jvm-test-fixtures",
    "plugins-jvm-test-suite",
    "plugins-test-report-aggregation",
    "plugins-version-catalog",
    "precondition-tester",
    "problems",
    "problems-api",
    "problems-rendering",
    "process-memory-services",
    "process-services",
    "public-api-tests",
    "publish",
    "reporting",
    "resources",
    "resources-gcs",
    "resources-http",
    "resources-s3",
    "resources-sftp",
    "samples",
    "scala",
    "security",
    "serialization",
    "service-lookup",
    "service-provider",
    "service-registry-builder",
    "service-registry-impl",
    "signing",
    "snapshots",
    "soak",
    "stdlib-java-extensions",
    "stdlib-kotlin-extensions",
    "stdlib-serialization-codecs",
    "test-kit",
    "test-suites-base",
    "testing-base",
    "testing-base-infrastructure",
    "testing-junit-platform",
    "testing-jvm",
    "testing-jvm-infrastructure",
    "testing-native",
    "time",
    "toolchains-jvm",
    "toolchains-jvm-shared",
    "tooling-api",
    "tooling-api-builders",
    "tooling-api-provider",
    "tooling-native",
    "unit-test-fixtures",
    "version-control",
    "war",
    "worker-main",
    "workers",
    "wrapper-main",
    "wrapper-shared",
)
