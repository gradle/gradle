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

package gradlebuild.performance

import com.google.common.annotations.VisibleForTesting
import com.gradle.enterprise.gradleplugin.testretry.TestRetryExtension
import gradlebuild.basics.BuildEnvironment.isIntel
import gradlebuild.basics.BuildEnvironment.isLinux
import gradlebuild.basics.BuildEnvironment.isMacOsX
import gradlebuild.basics.BuildEnvironment.isWindows
import gradlebuild.basics.accessors.groovy
import gradlebuild.basics.androidStudioHome
import gradlebuild.basics.autoDownloadAndroidStudio
import gradlebuild.basics.buildBranch
import gradlebuild.basics.buildCommitId
import gradlebuild.basics.defaultPerformanceBaselines
import gradlebuild.basics.includePerformanceTestScenarios
import gradlebuild.basics.logicalBranch
import gradlebuild.basics.performanceBaselines
import gradlebuild.basics.performanceDependencyBuildIds
import gradlebuild.basics.performanceGeneratorMaxProjects
import gradlebuild.basics.performanceTestVerbose
import gradlebuild.basics.propertiesForPerformanceDb
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.basics.repoRoot
import gradlebuild.basics.runAndroidStudioInHeadlessMode
import gradlebuild.capitalize
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.performance.Config.androidStudioVersion
import gradlebuild.performance.Config.defaultAndroidStudioJvmArgs
import gradlebuild.performance.generator.tasks.AbstractProjectGeneratorTask
import gradlebuild.performance.generator.tasks.JvmProjectGeneratorTask
import gradlebuild.performance.generator.tasks.ProjectGeneratorTask
import gradlebuild.performance.generator.tasks.TemplateProjectGeneratorTask
import gradlebuild.performance.tasks.BuildCommitDistribution
import gradlebuild.performance.tasks.DetermineBaselines
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.performance.tasks.PerformanceTestReport
import gradlebuild.toLowerCase
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.process.CommandLineArgumentProvider
import org.w3c.dom.Document
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant.now
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter.ofPattern
import java.util.concurrent.Callable
import javax.xml.parsers.DocumentBuilderFactory


object Config {
    const val performanceTestReportsDir = "performance-tests/report"
    const val performanceTestResultsJsonName = "perf-results.json"
    const val performanceTestResultsJson = "performance-tests/$performanceTestResultsJsonName"

    // Android Studio Hedgehog | 2023.1.1 Canary
    const val androidStudioVersion = "2023.1.1.6"
    val defaultAndroidStudioJvmArgs = listOf("-Xms256m", "-Xmx4096m")
}


@Suppress("unused")
class PerformanceTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val performanceTestSourceSet = createPerformanceTestSourceSet()
        addPerformanceTestConfigurationAndDependencies()
        configureGeneratorTasks()
        val cleanTestProjectsTask = createCleanTestProjectsTask()
        val performanceTestExtension = createExtension(performanceTestSourceSet, cleanTestProjectsTask)

        createAndWireCommitDistributionTask(performanceTestExtension)
        createAdditionalTasks(performanceTestSourceSet)
        configureIdePlugins(performanceTestSourceSet)
        configureAndroidStudioInstallation()
    }

    private
    fun Project.createExtension(performanceTestSourceSet: SourceSet, cleanTestProjectsTask: TaskProvider<Delete>): PerformanceTestExtension {
        val buildService = registerBuildService()
        val performanceTestExtension = extensions.create<PerformanceTestExtension>("performanceTest", this, performanceTestSourceSet, cleanTestProjectsTask, buildService)
        performanceTestExtension.baselines = project.performanceBaselines
        return performanceTestExtension
    }

    private
    fun Project.createPerformanceTestSourceSet(): SourceSet = the<SourceSetContainer>().run {
        val main by getting
        val test by getting
        val performanceTest by creating {
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
        }
        performanceTest
    }

    private
    fun Project.addPerformanceTestConfigurationAndDependencies() {
        addDependenciesAndConfigurations("performance")

        val junit by configurations.creating
        dependencies {
            "performanceTestImplementation"(project(":internal-performance-testing"))
            junit("junit:junit:4.13")
        }
    }

    private
    fun Project.configureGeneratorTasks() {

        tasks.withType<ProjectGeneratorTask>().configureEach {
            group = "Project setup"
        }

        tasks.withType<AbstractProjectGeneratorTask>().configureEach {
            project.performanceGeneratorMaxProjects?.let { maxProjects ->
                setProjects(maxProjects)
            }
        }

        tasks.withType<JvmProjectGeneratorTask>().configureEach {
            testDependencies = configurations["junit"]
        }

        tasks.withType<TemplateProjectGeneratorTask>().configureEach {
            sharedTemplateDirectory = project(":internal-performance-testing").file("src/templates")
        }
    }

    private
    fun Project.createCleanTestProjectsTask() =
        tasks.register<Delete>("cleanTestProjects")

    private
    fun Project.createAdditionalTasks(performanceSourceSet: SourceSet) {
        createPerformanceTestReportTask("performanceTestReport", "org.gradle.performance.results.report.DefaultReportGenerator")
        createPerformanceTestReportTask("performanceTestFlakinessReport", "org.gradle.performance.results.report.FlakinessReportGenerator")

        tasks.withType<PerformanceTestReport>().configureEach {
            classpath.from(performanceSourceSet.runtimeClasspath)
            performanceResultsDirectory = repoRoot().dir("perf-results")
            reportDir = project.layout.buildDirectory.dir(this@configureEach.name)
            databaseParameters = project.propertiesForPerformanceDb
            branchName = buildBranch
            channel.convention(branchName.map { "commits-$it" })
            channelPatterns.add(logicalBranch)
            channelPatterns.add(logicalBranch.map { "commits-pre-test/$it/%" })
            commitId = buildCommitId
            projectName = project.name
        }

        tasks.register<JavaExec>("writePerformanceTimes") {
            classpath(performanceSourceSet.runtimeClasspath)
            mainClass = "org.gradle.performance.results.PerformanceTestRuntimesGenerator"
            systemProperties(project.propertiesForPerformanceDb)
            args(repoRoot().file(".teamcity/performance-test-durations.json").asFile.absolutePath)
            doNotTrackState("Reads data from the database")
        }

        val performanceScenarioJson = repoRoot().file(".teamcity/performance-tests-ci.json").asFile
        val tmpPerformanceScenarioJson = project.layout.buildDirectory.file("performance-tests-ci.json").get().asFile
        createGeneratePerformanceDefinitionJsonTask("writePerformanceScenarioDefinitions", performanceSourceSet, performanceScenarioJson)
        val writeTmpPerformanceScenarioDefinitions = createGeneratePerformanceDefinitionJsonTask("writeTmpPerformanceScenarioDefinitions", performanceSourceSet, tmpPerformanceScenarioJson)

        tasks.register<JavaExec>("verifyPerformanceScenarioDefinitions") {
            dependsOn(writeTmpPerformanceScenarioDefinitions)
            classpath(performanceSourceSet.runtimeClasspath)
            mainClass = "org.gradle.performance.fixture.PerformanceTestScenarioDefinitionVerifier"
            args(performanceScenarioJson.absolutePath, tmpPerformanceScenarioJson.absolutePath)
            inputs.files(performanceSourceSet.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
            inputs.file(performanceScenarioJson.absolutePath)
            inputs.file(tmpPerformanceScenarioJson.absolutePath)
        }
    }

    private
    fun Project.createGeneratePerformanceDefinitionJsonTask(name: String, performanceSourceSet: SourceSet, outputJson: File) =
        tasks.register<Test>(name) {
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath
            maxParallelForks = 1
            systemProperty("org.gradle.performance.scenario.json", outputJson.absolutePath)

            outputs.cacheIf { false }
            outputs.file(outputJson)

            predictiveSelection.enabled = false
        }

    private
    fun Project.createPerformanceTestReportTask(name: String, reportGeneratorClass: String): TaskProvider<PerformanceTestReport> {
        val performanceTestReport = tasks.register<PerformanceTestReport>(name) {
            this.reportGeneratorClass = reportGeneratorClass
            this.dependencyBuildIds = project.performanceDependencyBuildIds
        }
        val performanceTestReportZipTask = performanceReportZipTaskFor(performanceTestReport)
        performanceTestReport {
            finalizedBy(performanceTestReportZipTask)
        }
        tasks.withType<Delete>().configureEach {
            if (name == "clean${name.capitalize()}") {
                // do not use 'tasks.named("clean...")' because that realizes the base task to apply the clean rule
                delete(performanceTestReport)
                dependsOn("clean${performanceTestReportZipTask.name.capitalize()}")
            }
        }
        return performanceTestReport
    }

    private
    fun Project.configureIdePlugins(performanceTestSourceSet: SourceSet) {
        val performanceTestCompileClasspath by configurations
        val performanceTestRuntimeClasspath by configurations
        plugins.withType<EclipsePlugin> {
            configure<EclipseModel> {
                classpath {
                    plusConfigurations.apply {
                        add(performanceTestCompileClasspath)
                        add(performanceTestRuntimeClasspath)
                    }
                }
            }
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSources.from(performanceTestSourceSet.java.srcDirs, performanceTestSourceSet.groovy.srcDirs)
                    testResources.from(performanceTestSourceSet.resources.srcDirs)
                }
            }
        }
    }

    private
    fun Project.configureAndroidStudioInstallation() {
        repositories {
            ivy {
                // Url of Android Studio archive
                url = uri("https://redirector.gvt1.com/edgedl/android/studio/ide-zips")
                patternLayout {
                    artifact("[revision]/[artifact]-[revision]-[ext]")
                }
                metadataSources { artifact() }
                content {
                    includeGroup("android-studio")
                }
            }
        }

        val androidStudioRuntime by configurations.creating
        dependencies {
            val extension = when {
                isWindows -> "windows.zip"
                isMacOsX && isIntel -> "mac.zip"
                isMacOsX && !isIntel -> "mac_arm.zip"
                isLinux -> "linux.tar.gz"
                else -> throw IllegalStateException("Unsupported OS: ${OperatingSystem.current()}")
            }
            androidStudioRuntime("android-studio:android-studio:$androidStudioVersion@$extension")
        }

        tasks.register<Copy>("unpackAndroidStudio") {
            from(
                Callable {
                    val singleFile = androidStudioRuntime.singleFile
                    when {
                        singleFile.name.endsWith(".tar.gz") -> tarTree(singleFile)
                        else -> zipTree(singleFile)
                    }
                }
            ) {
                eachFile {
                    // Remove top folder when unzipping, that way we get rid of Android Studio.app folder that can cause issues on Mac
                    // where MacOS would kill the Android Studio process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
            }
            into(layout.buildDirectory.dir("android-studio"))
        }
    }

    private
    fun Project.registerBuildService(): Provider<PerformanceTestService> =
        gradle.sharedServices.registerIfAbsent("performanceTestService", PerformanceTestService::class) {
            maxParallelUsages = 1
        }

    private
    fun Project.createAndWireCommitDistributionTask(extension: PerformanceTestExtension) {
        // The data flow here is:
        // extension.baselines -> determineBaselines.configuredBaselines
        // determineBaselines.determinedBaselines -> performanceTest.baselines
        // determineBaselines.determinedBaselines -> buildCommitDistribution.baselines
        val determineBaselines = tasks.register("determineBaselines", DetermineBaselines::class, false)
        val buildCommitDistribution = tasks.register("buildCommitDistribution", BuildCommitDistribution::class)
        val buildCommitDistributionsDir = project.rootProject.layout.buildDirectory.dir("commit-distributions")

        determineBaselines.configure {
            configuredBaselines = extension.baselines
            defaultBaselines = project.defaultPerformanceBaselines
            logicalBranch = project.logicalBranch
        }

        buildCommitDistribution.configure {
            dependsOn(determineBaselines)
            releasedVersionsFile = project.releasedVersionsFile()
            commitBaseline = determineBaselines.flatMap { it.determinedBaselines }
            commitDistribution = buildCommitDistributionsDir.zip(commitBaseline) { dir, version -> dir.file("gradle-$version.zip") }
            commitDistributionToolingApiJar = buildCommitDistributionsDir.zip(commitBaseline) { dir, version -> dir.file("gradle-$version-tooling-api.jar") }
        }

        tasks.withType<PerformanceTest>().configureEach {
            dependsOn(buildCommitDistribution)
            commitDistributionsDir = buildCommitDistributionsDir
            baselines = determineBaselines.flatMap { it.determinedBaselines }
        }
    }
}


private
fun Project.performanceReportZipTaskFor(performanceReport: TaskProvider<out PerformanceTestReport>): TaskProvider<Zip> =
    tasks.register("${performanceReport.name}ResultsZip", Zip::class) {
        from(performanceReport.get().reportDir.locationOnly) {
            into("report")
        }
        from(performanceReport.get().performanceResults) {
            into("perf-results")
        }
        destinationDirectory = layout.buildDirectory
        archiveFileName = "performance-test-results.zip"
    }


abstract
class PerformanceTestExtension(
    private val project: Project,
    private val performanceSourceSet: SourceSet,
    private val cleanTestProjectsTask: TaskProvider<Delete>,
    private val buildService: Provider<PerformanceTestService>
) {
    private
    val registeredPerformanceTests: MutableList<TaskProvider<out Task>> = mutableListOf()

    private
    val registeredTestProjects: MutableList<TaskProvider<out Task>> = mutableListOf()

    abstract
    val baselines: Property<String>

    inline fun <reified T : Task> registerTestProject(testProject: String, noinline configuration: T.() -> Unit): TaskProvider<T> =
        registerTestProject(testProject, T::class.java, configuration)

    fun <T : Task> registerTestProject(testProject: String, type: Class<T>, configurationAction: Action<in T>): TaskProvider<T> {
        return doRegisterTestProject(testProject, type, configurationAction)
    }

    fun <T : Task> registerAndroidTestProject(testProject: String, type: Class<T>, configurationAction: Action<in T>): TaskProvider<T> {
        return doRegisterTestProject(testProject, type, configurationAction) {
            // AndroidStudio jvmArgs could be set per project, but at the moment that is not necessary
            jvmArgumentProviders.add(getAndroidProjectJvmArguments(defaultAndroidStudioJvmArgs))
            environment("JAVA_HOME", LazyEnvironmentVariable { javaLauncher.get().metadata.installationPath.asFile.absolutePath })
        }
    }

    private
    fun getAndroidProjectJvmArguments(androidStudioJvmArgs: List<String>): CommandLineArgumentProvider {
        val unpackAndroidStudio = project.tasks.named("unpackAndroidStudio", Copy::class.java)
        val androidStudioInstallation = project.objects.newInstance<AndroidStudioInstallation>().apply {
            studioInstallLocation.fileProvider(unpackAndroidStudio.map { it.destinationDir })
        }
        return AndroidStudioSystemProperties(
            androidStudioInstallation,
            project.autoDownloadAndroidStudio,
            project.runAndroidStudioInHeadlessMode,
            project.androidStudioHome,
            androidStudioJvmArgs,
            project.providers
        )
    }

    private
    fun <T : Task> doRegisterTestProject(testProject: String, type: Class<T>, configurationAction: Action<in T>, testSpecificConfigurator: PerformanceTest.() -> Unit = {}): TaskProvider<T> {
        val generatorTask = project.tasks.register(testProject, type, configurationAction)
        val currentlyRegisteredTestProjects = registeredTestProjects.toList()
        cleanTestProjectsTask.configure {
            delete(generatorTask.map { it.outputs })
        }
        registeredPerformanceTests.forEach {
            it.configure { mustRunAfter(generatorTask) }
        }
        registeredPerformanceTests.add(
            createPerformanceTest("${testProject}PerformanceAdHocTest", generatorTask) {
                description = "Runs ad-hoc performance tests on $testProject - can be used locally"
                channel = "adhoc"
                outputs.doNotCacheIf("Is adhoc performance test") { true }
                mustRunAfter(currentlyRegisteredTestProjects)
                testSpecificConfigurator(this)

                extensions.findByType<TestRetryExtension>()?.maxRetries = 0
            }
        )

        val channelSuffix = if (OperatingSystem.current().isLinux) "" else "-${OperatingSystem.current().familyName.toLowerCase()}"

        registeredPerformanceTests.add(
            createPerformanceTest("${testProject}PerformanceTest", generatorTask) {
                description = "Runs performance tests on $testProject - supposed to be used on CI"
                channel = "commits$channelSuffix"

                extensions.findByType<TestRetryExtension>()?.maxRetries = 1

                if (project.includePerformanceTestScenarios) {
                    val scenariosFromFile = project.loadScenariosFromFile(testProject)
                    if (scenariosFromFile.isNotEmpty()) {
                        setTestNameIncludePatterns(scenariosFromFile)
                    }
                    doFirst {
                        assert((filter as DefaultTestFilter).includePatterns.isNotEmpty()) { "Running $name requires to add a test filter" }
                    }
                }
                mustRunAfter(currentlyRegisteredTestProjects)
                testSpecificConfigurator(this)
            }
        )
        registeredTestProjects.add(generatorTask)
        return generatorTask
    }

    private
    fun createPerformanceTest(name: String, generatorTask: TaskProvider<out Task>, configure: PerformanceTest.() -> Unit = {}): TaskProvider<out PerformanceTest> {
        val performanceTest = project.tasks.register(name, PerformanceTest::class) {
            group = "verification"
            buildId = System.getenv("BUILD_ID") ?: "localBuild-${ofPattern("yyyyMMddHHmmss").withZone(systemDefault()).format(now())}"
            reportDir = project.layout.buildDirectory.file("${this.name}/${Config.performanceTestReportsDir}").get().asFile
            resultsJson = project.layout.buildDirectory.file("${this.name}/${Config.performanceTestResultsJson}").get().asFile
            addDatabaseParameters(project.propertiesForPerformanceDb)
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath

            usesService(buildService)
            performanceTestService = buildService

            testProjectName = generatorTask.name
            testProjectFiles.from(generatorTask)

            val gradleBuildBranch = project.buildBranch.get()
            branchName = gradleBuildBranch
            commitId = project.buildCommitId

            reportGeneratorClass = "org.gradle.performance.results.report.DefaultReportGenerator"

            maxParallelForks = 1
            useJUnitPlatform()
            // We need 5G of heap to parse large JFR recordings when generating flamegraphs.
            // If we drop JFR as profiler and switch to something else, we can reduce the memory.
            jvmArgs("-Xmx5g", "-XX:+HeapDumpOnOutOfMemoryError")
            if (project.performanceTestVerbose.isPresent) {
                testLogging.showStandardStreams = true
            }
        }
        performanceTest.configure(configure)

        val testResultsZipTask = project.testResultsZipTaskFor(performanceTest)
        performanceTest.configure {
            finalizedBy(testResultsZipTask)
        }
        project.tasks.withType<Delete>().configureEach {
            // do not use 'tasks.named("clean...")' because that realizes the base task to apply the clean rule
            if (name == "clean${name.capitalize()}") {
                delete(performanceTest)
                dependsOn("clean${testResultsZipTask.name.capitalize()}")
            }
        }
        return performanceTest
    }

    private
    fun Project.testResultsZipTaskFor(performanceTest: TaskProvider<out PerformanceTest>): TaskProvider<Zip> =
        tasks.register("${performanceTest.name}ResultsZip", Zip::class) {
            val junitXmlDir = performanceTest.get().reports.junitXml.outputLocation.asFile.get()
            from(junitXmlDir) {
                include("**/TEST-*.xml")
                includeEmptyDirs = false
                eachFile {
                    try {
                        // skip files where all tests were skipped
                        if (allTestsWereSkipped(file)) {
                            exclude()
                        }
                    } catch (e: Exception) {
                        exclude()
                    }
                }
            }
            from(performanceTest.get().debugArtifactsDirectory) {
                // Rename the json file specific per task, so we can copy multiple of those files from one build on Teamcity
                rename(Config.performanceTestResultsJsonName, "perf-results-${performanceTest.name}.json")
            }
            destinationDirectory = project.layout.buildDirectory
            archiveFileName = "test-results-${junitXmlDir.name}.zip"
        }
}


abstract class AndroidStudioInstallation {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val studioInstallLocation: DirectoryProperty
}


class AndroidStudioSystemProperties(
    @get:Internal
    val studioInstallation: AndroidStudioInstallation,
    @get:Internal
    val autoDownloadAndroidStudio: Boolean,
    @get:Internal
    val runAndroidStudioInHeadlessMode: Boolean,
    @get:Internal
    val androidStudioHome: Provider<String>,
    @get:Internal
    val androidStudioJvmArgs: List<String>,
    providers: ProviderFactory
) : CommandLineArgumentProvider {

    @get:Optional
    @get:Nested
    val studioInstallationProvider = providers.provider {
        if (autoDownloadAndroidStudio) {
            studioInstallation
        } else {
            null
        }
    }

    override fun asArguments(): Iterable<String> {
        val systemProperties = mutableListOf<String>()
        if (autoDownloadAndroidStudio) {
            val androidStudioPath = studioInstallation.studioInstallLocation.asFile.get().absolutePath
            systemProperties.add("-Dstudio.home=$androidStudioPath")
        } else {
            if (androidStudioHome.isPresent) {
                systemProperties.add("-Dstudio.home=${androidStudioHome.get()}")
            }
        }
        if (runAndroidStudioInHeadlessMode) {
            systemProperties.add("-Dstudio.tests.headless=true")
        }
        if (androidStudioJvmArgs.isNotEmpty()) {
            systemProperties.add("-DstudioJvmArgs=${androidStudioJvmArgs.joinToString(separator = ",")}")
        }
        return systemProperties
    }
}


class LazyEnvironmentVariable(private val callable: Callable<String>) {
    override fun toString(): String {
        return callable.call()
    }
}


private
fun Project.loadScenariosFromFile(testProject: String): List<String> {
    val scenarioFile = repoRoot().file("performance-test-splits/include-$testProject-performance-scenarios.csv").asFile
    return if (scenarioFile.isFile)
        scenarioFile.readLines(StandardCharsets.UTF_8)
            .filter { it.isNotEmpty() }
            .map {
                val (className, scenario) = it.split(";")
                "$className.$scenario"
            }
    else listOf()
}


@VisibleForTesting
fun allTestsWereSkipped(junitXmlFile: File): Boolean =
    parseXmlFile(junitXmlFile).documentElement.run {
        getAttribute("tests") == getAttribute("skipped")
    }


private
fun parseXmlFile(file: File): Document =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
