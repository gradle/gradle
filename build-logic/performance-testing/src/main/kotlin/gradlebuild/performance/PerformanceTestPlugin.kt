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
import gradlebuild.basics.accessors.groovy
import gradlebuild.basics.kotlindsl.selectStringProperties
import gradlebuild.basics.kotlindsl.stringPropertyOrNull
import gradlebuild.basics.repoRoot
import gradlebuild.identity.extension.ModuleIdentityExtension
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.performance.generator.tasks.AbstractProjectGeneratorTask
import gradlebuild.performance.generator.tasks.JvmProjectGeneratorTask
import gradlebuild.performance.generator.tasks.ProjectGeneratorTask
import gradlebuild.performance.generator.tasks.TemplateProjectGeneratorTask
import gradlebuild.performance.tasks.BuildCommitDistribution
import gradlebuild.performance.tasks.DetermineBaselines
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.performance.tasks.PerformanceTestReport
import gradlebuild.performance.tasks.RebaselinePerformanceTests
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
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
import org.w3c.dom.Document
import java.io.File
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory


object PropertyNames {
    const val dbUrl = "org.gradle.performance.db.url"
    const val dbUsername = "org.gradle.performance.db.username"
    const val dbPassword = "org.gradle.performance.db.password"
    const val dbPasswordEnv = "PERFORMANCE_DB_PASSWORD_TCAGENT"

    const val performanceTestVerbose = "performanceTest.verbose"

    const val baselines = "performanceBaselines"
}


object Config {

    const val performanceTestScenarioListFileName = "performance-tests/scenario-list.csv"

    const val performanceTestReportsDir = "performance-tests/report"

    const val performanceTestResultsJsonName = "perf-results.json"
    const val performanceTestResultsJson = "performance-tests/$performanceTestResultsJsonName"

    const val teamCityUrl = "https://builds.gradle.org/"
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
        createRebaselineTask(performanceTestSourceSet)
        configureIdePlugins(performanceTestSourceSet)
    }

    private
    fun Project.createExtension(performanceTestSourceSet: SourceSet, cleanTestProjectsTask: TaskProvider<Delete>): PerformanceTestExtension {
        val buildService = registerBuildService()
        val performanceTestExtension = extensions.create<PerformanceTestExtension>("performanceTest", this, performanceTestSourceSet, cleanTestProjectsTask, buildService)
        performanceTestExtension.baselines.set(stringPropertyOrNull(PropertyNames.baselines))
        return performanceTestExtension
    }

    private
    fun Project.createRebaselineTask(performanceTestSourceSet: SourceSet) {
        project.tasks.register("rebaselinePerformanceTests", RebaselinePerformanceTests::class) {
            source(performanceTestSourceSet.allSource)
        }
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
            (project.findProperty("maxProjects") as? Int)?.let { maxProjects ->
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
            performanceResultsDirectory.set(repoRoot().dir("perf-results"))
            reportDir.set(project.layout.buildDirectory.dir(this@configureEach.name))
            databaseParameters.set(project.propertiesForPerformanceDb())
            val moduleIdentity = project.the<ModuleIdentityExtension>()
            branchName.set(moduleIdentity.gradleBuildBranch)
            channel.convention(branchName.map { "commits-$it" })
            commitId.set(moduleIdentity.gradleBuildCommitId)
            projectName.set(project.name)
        }

        tasks.register<JavaExec>("writePerformanceTimes") {
            classpath(performanceSourceSet.runtimeClasspath)
            mainClass.set("org.gradle.performance.results.PerformanceTestRuntimesGenerator")
            systemProperties(project.propertiesForPerformanceDb())
            args(repoRoot().file(".teamcity/performance-test-durations.json").asFile.absolutePath)
            // Never up-to-date since it reads data from the database.
            outputs.upToDateWhen { false }
        }

        val performanceScenarioJson = repoRoot().file(".teamcity/performance-tests-ci.json").asFile
        val tmpPerformanceScenarioJson = project.layout.buildDirectory.file("performance-tests-ci.json").get().asFile
        createGeneratePerformanceDefinitionJsonTask("writePerformanceScenarioDefinitions", performanceSourceSet, performanceScenarioJson)
        val writeTmpPerformanceScenarioDefinitions = createGeneratePerformanceDefinitionJsonTask("writeTmpPerformanceScenarioDefinitions", performanceSourceSet, tmpPerformanceScenarioJson)

        tasks.register<JavaExec>("verifyPerformanceScenarioDefinitions") {
            dependsOn(writeTmpPerformanceScenarioDefinitions)
            classpath(performanceSourceSet.runtimeClasspath)
            mainClass.set("org.gradle.performance.fixture.PerformanceTestScenarioDefinitionVerifier")
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
        }

    private
    fun Project.createPerformanceTestReportTask(name: String, reportGeneratorClass: String): TaskProvider<PerformanceTestReport> {
        val performanceTestReport = tasks.register<PerformanceTestReport>(name) {
            this.reportGeneratorClass.set(reportGeneratorClass)
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
                    testSourceDirs = testSourceDirs + performanceTestSourceSet.groovy.srcDirs
                    testResourceDirs = testResourceDirs + performanceTestSourceSet.resources.srcDirs
                }
            }
        }
    }

    private
    fun Project.registerBuildService(): Provider<PerformanceTestService> =
        gradle.sharedServices.registerIfAbsent("performanceTestService", PerformanceTestService::class) {
            maxParallelUsages.set(1)
        }

    private
    fun Project.createAndWireCommitDistributionTask(extension: PerformanceTestExtension) {
        // The data flow here is:
        // extension.baselines -> determineBaselines.configuredBaselines
        // determineBaselines.determinedBaselines -> performanceTest.baselines
        // determineBaselines.determinedBaselines -> buildCommitDistribution.baselines
        val determineBaselines = tasks.register("determineBaselines", DetermineBaselines::class, false)
        val buildCommitDistribution = tasks.register("buildCommitDistribution", BuildCommitDistribution::class)

        determineBaselines.configure {
            configuredBaselines.set(extension.baselines)
        }

        buildCommitDistribution.configure {
            dependsOn(determineBaselines)
            commitBaseline.set(determineBaselines.flatMap { it.determinedBaselines })
        }

        tasks.withType<PerformanceTest>().configureEach {
            dependsOn(buildCommitDistribution)
            baselines.set(determineBaselines.flatMap { it.determinedBaselines })
        }
    }
}


private
fun Project.propertiesForPerformanceDb(): Map<String, String> {
    val password = providers.environmentVariable(PropertyNames.dbPasswordEnv).forUseAtConfigurationTime()
    return if (password.isPresent) {
        selectStringProperties(
            PropertyNames.dbUrl,
            PropertyNames.dbUsername
        ) + (PropertyNames.dbPassword to password.get())
    } else {
        selectStringProperties(
            PropertyNames.dbUrl,
            PropertyNames.dbUsername
        )
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
        destinationDirectory.set(buildDir)
        archiveFileName.set("performance-test-results.zip")
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

    private
    val shouldLoadScenariosFromFile = project.providers.gradleProperty("includePerformanceTestScenarios")
        .forUseAtConfigurationTime()
        .getOrElse("false") == "true"

    abstract
    val baselines: Property<String>

    inline fun <reified T : Task> registerTestProject(testProject: String, noinline configuration: T.() -> Unit): TaskProvider<T> =
        registerTestProject(testProject, T::class.java, configuration)

    fun <T : Task> registerTestProject(testProject: String, type: Class<T>, configurationAction: Action<in T>): TaskProvider<T> {
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

                retry {
                    maxRetries.set(0)
                }
            }
        )

        val channelSuffix = if (OperatingSystem.current().isLinux) "" else "-${OperatingSystem.current().familyName.toLowerCase()}"

        registeredPerformanceTests.add(
            createPerformanceTest("${testProject}PerformanceTest", generatorTask) {
                description = "Runs performance tests on $testProject - supposed to be used on CI"
                channel = "commits$channelSuffix"

                retry {
                    maxRetries.set(1)
                }

                if (shouldLoadScenariosFromFile) {
                    val scenariosFromFile = project.loadScenariosFromFile(testProject)
                    if (scenariosFromFile.isNotEmpty()) {
                        setTestNameIncludePatterns(scenariosFromFile)
                    }
                    doFirst {
                        assert((filter as DefaultTestFilter).includePatterns.isNotEmpty()) { "Running $name requires to add a test filter" }
                    }
                }
                mustRunAfter(currentlyRegisteredTestProjects)
            }
        )
        registeredTestProjects.add(generatorTask)
        return generatorTask
    }

    private
    fun createPerformanceTest(name: String, generatorTask: TaskProvider<out Task>, configure: PerformanceTest.() -> Unit = {}): TaskProvider<out PerformanceTest> {
        val performanceTest = project.tasks.register(name, PerformanceTest::class) {
            group = "verification"
            buildId = System.getenv("BUILD_ID")
            reportDir = project.layout.buildDirectory.file("${this.name}/${Config.performanceTestReportsDir}").get().asFile
            resultsJson = project.layout.buildDirectory.file("${this.name}/${Config.performanceTestResultsJson}").get().asFile
            addDatabaseParameters(project.propertiesForPerformanceDb())
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath

            performanceTestService.set(buildService)

            testProjectName.set(generatorTask.name)
            testProjectFiles.from(generatorTask)

            val identityExtension = project.the<ModuleIdentityExtension>()
            val gradleBuildBranch = identityExtension.gradleBuildBranch.get()
            branchName = gradleBuildBranch
            systemProperty("gradleBuildBranch", gradleBuildBranch)
            commitId.set(identityExtension.gradleBuildCommitId)

            reportGeneratorClass.set("org.gradle.performance.results.report.DefaultReportGenerator")

            maxParallelForks = 1
            useJUnitPlatform()
            // We need 5G of heap to parse large JFR recordings when generating flamegraphs.
            // If we drop JFR as profiler and switch to something else, we can reduce the memory.
            jvmArgs("-Xmx5g", "-XX:+HeapDumpOnOutOfMemoryError")
            if (project.hasProperty(PropertyNames.performanceTestVerbose)) {
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
            val junitXmlDir = performanceTest.get().reports.junitXml.destination
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
            destinationDirectory.set(project.layout.buildDirectory)
            archiveFileName.set("test-results-${junitXmlDir.name}.zip")
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
