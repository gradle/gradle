package org.gradle.plugins.performance

import accessors.groovy
import accessors.java
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.internal.hash.HashUtil
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.DistributedPerformanceTest
import org.gradle.testing.PerformanceTest
import org.gradle.testing.RebaselinePerformanceTests
import org.gradle.testing.performance.generator.tasks.AbstractProjectGeneratorTask
import org.gradle.testing.performance.generator.tasks.JavaExecProjectGeneratorTask
import org.gradle.testing.performance.generator.tasks.JvmProjectGeneratorTask
import org.gradle.testing.performance.generator.tasks.ProjectGeneratorTask
import org.gradle.testing.performance.generator.tasks.RemoteProject
import org.gradle.testing.performance.generator.tasks.TemplateProjectGeneratorTask

import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

import org.gradle.kotlin.dsl.*


private
object PropertyNames {

    const val dbUrl = "org.gradle.performance.db.url"
    const val dbUsername = "org.gradle.performance.db.username"
    const val dbPassword = "org.gradle.performance.db.password"

    const val useYourkit = "org.gradle.performance.use_yourkit"
    const val honestProfiler = "org.gradle.performance.honestprofiler"

    const val workerTestTaskName = "org.gradle.performance.workerTestTaskName"
    const val channel = "org.gradle.performance.execution.channel"
    const val coordinatorBuildId = "org.gradle.performance.coordinatorBuildId"
    const val performanceTestVerbose = "performanceTest.verbose"
    const val baselines = "org.gradle.performance.baselines"
    const val buildTypeId = "org.gradle.performance.buildTypeId"
    const val branchName = "org.gradle.performance.branchName"

    const val teamCityUsername = "teamCityUsername"
    const val teamCityPassword = "teamCityPassword"
}


private
object Config {

    val baseLineList = listOf("1.1", "1.12", "2.0", "2.1", "2.4", "2.9", "2.12", "2.14.1", "last")

    const val performanceTestScenarioListFileName = "performance-tests/scenario-list.csv"

    const val performanceTestScenarioReportFileName = "performance-tests/scenario-report.html"

    const val performanceTestReportsDir = "performance-tests/report"

    const val teamCityUrl = "https://builds.gradle.org/"

    const val adhocTestDbUrl = "jdbc:h2:./build/database"
}


private
const val performanceExperimentCategory = "org.gradle.performance.categories.PerformanceExperiment"


@Suppress("unused")
class PerformanceTestPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        apply {
            plugin("java")
        }

        val performanceTestSourceSet = createPerformanceTestSourceSet()
        addConfigurationAndDependencies()
        createCheckNoIdenticalBuildFilesTask()
        configureGeneratorTasks()

        val prepareSamplesTask = createPrepareSamplesTask()
        createCleanSamplesTask()

        val performanceReportTask = createPerformanceReportTask(performanceTestSourceSet)
        createLocalPerformanceTestTasks(performanceTestSourceSet, prepareSamplesTask, performanceReportTask)
        createDistributedPerformanceTestTasks(performanceTestSourceSet, prepareSamplesTask, performanceReportTask)

        createRebaselineTask(performanceTestSourceSet)

        configureIdePlugins(performanceTestSourceSet)
    }

    private
    fun Project.createRebaselineTask(performanceTestSourceSet: SourceSet) {
        project.tasks.create<RebaselinePerformanceTests>("rebaselinePerformanceTests") {
            source(performanceTestSourceSet.allSource)
        }
    }

    private
    fun Project.createPerformanceTestSourceSet(): SourceSet = java.sourceSets.run {
        val main by getting
        val test by getting
        val performanceTest by creating {
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
        }
        performanceTest
    }

    private
    fun Project.addConfigurationAndDependencies() {
        configurations {

            val testCompile by getting

            "performanceTestCompile" {
                extendsFrom(testCompile)
            }

            val testRuntime by getting

            "performanceTestRuntime" {
                extendsFrom(testRuntime)
            }

            val performanceTestRuntimeClasspath by getting

            "partialDistribution" {
                extendsFrom(performanceTestRuntimeClasspath)
            }

            "junit" {
            }
        }

        dependencies {
            "performanceTestCompile"(project(":internalPerformanceTesting"))
            "junit"("junit:junit:4.12")
        }
    }

    private
    fun Project.createCheckNoIdenticalBuildFilesTask() {
        tasks.create("checkNoIdenticalBuildFiles") {
            doLast {
                val filesBySha1 = mutableMapOf<String, MutableList<File>>()
                buildDir.walkTopDown().forEach { file ->
                    if (file.name.endsWith(".gradle")) {
                        val sha1 = sha1StringFor(file)
                        val files = filesBySha1[sha1]
                        when (files) {
                            null -> filesBySha1[sha1] = mutableListOf(file)
                            else -> files.add(file)
                        }
                    }
                }

                filesBySha1.forEach { hash, candidates ->
                    if (candidates.size > 1) {
                        println("Duplicate build files found for hash '$hash' : $candidates")
                    }
                }
            }
        }
    }

    private
    fun Project.configureGeneratorTasks() {

        tasks.withType<ProjectGeneratorTask> {
            group = "Project setup"
        }

        tasks.withType<AbstractProjectGeneratorTask> {
            (project.findProperty("maxProjects") as? Int)?.let { maxProjects ->
                setProjects(maxProjects)
            }
        }

        tasks.withType<JvmProjectGeneratorTask> {
            testDependencies = configurations["junit"]
        }

        tasks.withType<TemplateProjectGeneratorTask> {
            sharedTemplateDirectory = project(":internalPerformanceTesting").file("src/templates")
        }
    }

    private
    fun Project.createPrepareSamplesTask(): Task =
        tasks.create("prepareSamples") {
            group = "Project Setup"
            description = "Generates all sample projects for automated performance tests"
            dependsOn(tasks.withType<ProjectGeneratorTask>())
            dependsOn(tasks.withType<RemoteProject>())
            dependsOn(tasks.withType<JavaExecProjectGeneratorTask>())
        }

    private
    fun Project.createCleanSamplesTask(): Task =
        tasks.create<Delete>("cleanSamples") {
            delete(deferred { tasks.withType<ProjectGeneratorTask>().map { it.outputs } })
            delete(deferred { tasks.withType<RemoteProject>().map { it.outputDirectory } })
            delete(deferred { tasks.withType<JavaExecProjectGeneratorTask>().map { it.outputs } })
        }

    private
    fun Project.createPerformanceReportTask(performanceTestSourceSet: SourceSet): PerformanceReport =
        tasks.create<PerformanceReport>("performanceReport") {
            systemProperties(propertiesForPerformanceDb())
            classpath = performanceTestSourceSet.runtimeClasspath
            resultStoreClass = "org.gradle.performance.results.AllResultsStore"
            reportDir = buildDir / Config.performanceTestReportsDir
            outputs.upToDateWhen { false }
        }

    private
    fun Project.createLocalPerformanceTestTasks(
        performanceSourceSet: SourceSet,
        prepareSamplesTask: Task,
        performanceReportTask: PerformanceReport
    ) {

        fun create(name: String, configure: PerformanceTest.() -> Unit = {}) {
            createLocalPerformanceTestTask(name, performanceSourceSet, prepareSamplesTask, performanceReportTask)
                .configure()
        }

        create("performanceTest") {
            (options as JUnitOptions).excludeCategories(performanceExperimentCategory)
        }

        create("performanceExperiment") {
            (options as JUnitOptions).includeCategories(performanceExperimentCategory)
        }

        create("fullPerformanceTest")

        create("performanceAdhocTest") {
            systemProperty(PropertyNames.dbUrl, Config.adhocTestDbUrl)
            channel = "adhoc"
        }
    }

    private
    fun Project.createDistributedPerformanceTestTasks(
        performanceSourceSet: SourceSet,
        prepareSamplesTask: Task,
        performanceReportTask: PerformanceReport
    ) {

        fun create(name: String, configure: PerformanceTest.() -> Unit = {}) {
            createDistributedPerformanceTestTask(name, performanceSourceSet, prepareSamplesTask, performanceReportTask)
                .configure()
        }

        create("distributedPerformanceTest") {
            (options as JUnitOptions).excludeCategories(performanceExperimentCategory)
            channel = "commits"
        }
        create("distributedPerformanceExperiment") {
            (options as JUnitOptions).includeCategories(performanceExperimentCategory)
            channel = "experiments"
        }
        create("distributedFullPerformanceTest") {
            baselines = Config.baseLineList.toString()
            checks = "none"
            channel = "historical"
        }
    }

    private
    fun Project.configureIdePlugins(performanceTestSourceSet: SourceSet) {
        val performanceTestCompile by configurations
        val performanceTestRuntime by configurations
        plugins.withType<EclipsePlugin> {
            configure<EclipseModel> {
                classpath {
                    plusConfigurations.apply {
                        add(performanceTestCompile)
                        add(performanceTestRuntime)
                    }
                }
            }
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSourceDirs = testSourceDirs + performanceTestSourceSet.groovy.srcDirs
                    testSourceDirs = testSourceDirs + performanceTestSourceSet.resources.srcDirs
                    scopes["TEST"]!!["plus"]!!.apply {
                        add(performanceTestCompile)
                        add(performanceTestRuntime)
                    }
                }
            }
        }
    }

    private
    fun Project.createDistributedPerformanceTestTask(
        name: String,
        performanceSourceSet: SourceSet,
        prepareSamplesTask: Task,
        performanceReportTask: PerformanceReport
    ): DistributedPerformanceTest =

        tasks.create<DistributedPerformanceTest>(name) {
            configureForAnyPerformanceTestTask(this, performanceSourceSet, prepareSamplesTask, performanceReportTask)
            scenarioList = buildDir / Config.performanceTestScenarioListFileName
            scenarioReport = buildDir / Config.performanceTestScenarioReportFileName
            buildTypeId = stringPropertyOrNull(PropertyNames.buildTypeId)
            workerTestTaskName = stringPropertyOrNull(PropertyNames.workerTestTaskName) ?: "fullPerformanceTest"
            coordinatorBuildId = stringPropertyOrNull(PropertyNames.coordinatorBuildId)
            branchName = stringPropertyOrNull(PropertyNames.branchName)
            teamCityUrl = Config.teamCityUrl
            teamCityUsername = stringPropertyOrNull(PropertyNames.teamCityUsername)
            teamCityPassword = stringPropertyOrNull(PropertyNames.teamCityPassword)
            afterEvaluate {
                branchName?.takeIf { it.isNotEmpty() }?.let { branchName ->
                    channel = channel + "-" + branchName
                }
            }
        }

    private
    fun Project.createLocalPerformanceTestTask(
        name: String,
        performanceSourceSet: SourceSet,
        prepareSamplesTask: Task,
        performanceReportTask: PerformanceReport
    ): PerformanceTest {

        val cleanTaskName = "clean${name.capitalize()}"

        val task = tasks.create<PerformanceTest>(name) {

            configureForAnyPerformanceTestTask(this, performanceSourceSet, prepareSamplesTask, performanceReportTask)

            if (project.hasProperty(PropertyNames.useYourkit)) {
                testLogging.showStandardStreams = true
                systemProperties[PropertyNames.useYourkit] = "1"
                outputs.upToDateWhen { false }
            }
            if (project.hasProperty(PropertyNames.honestProfiler)) {
                systemProperties[PropertyNames.honestProfiler] = "1"
            }
            if (project.hasProperty(PropertyNames.performanceTestVerbose)) {
                testLogging.showStandardStreams = true
            }

            val testResultsZipTask = testResultsZipTaskFor(this, name)
            finalizedBy(testResultsZipTask)
            val cleanTestResultsZipTask = tasks.create<Delete>("clean${testResultsZipTask.name.capitalize()}") {
                delete(testResultsZipTask.archivePath)
            }
            tasks.getByName(cleanTaskName) {
                dependsOn(cleanTestResultsZipTask)
            }
        }

        tasks.getByName(cleanTaskName) {
            delete(task.outputs)
            dependsOn("clean${performanceReportTask.name.capitalize()}")
        }

        return task
    }

    private
    fun Project.testResultsZipTaskFor(performanceTest: PerformanceTest, name: String): Zip {
        val junitXmlDir = performanceTest.reports.junitXml.destination
        return tasks.create<Zip>("${name}ResultsZip") {
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
            from(performanceTest.debugArtifactsDirectory)
            destinationDir = buildDir
            archiveName = "test-results-${junitXmlDir.name}.zip"
        }
    }

    private
    fun Project.configureForAnyPerformanceTestTask(
        task: PerformanceTest,
        performanceSourceSet: SourceSet,
        prepareSamplesTask: Task,
        performanceReportTask: PerformanceReport
    ) {

        task.apply {
            group = "verification"
            systemProperties(propertiesForPerformanceDb())
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath

            binaryDistributions.binZipRequired = true
            libsRepository.required = true
            maxParallelForks = 1

            project.findProperty(PropertyNames.baselines)?.let { baselines ->
                systemProperty(PropertyNames.baselines, baselines)
            }

            dependsOn(prepareSamplesTask)
            finalizedBy(performanceReportTask)

            mustRunAfter(tasks.withType<ProjectGeneratorTask>())
            mustRunAfter(tasks.withType<RemoteProject>())
            mustRunAfter(tasks.withType<JavaExecProjectGeneratorTask>())

            doFirst {
                channel?.let { channel ->
                    performanceReportTask.systemProperty(PropertyNames.channel, channel)
                }
            }
        }
    }

    private
    fun Project.propertiesForPerformanceDb(): Map<String, String> =
        selectStringProperties(
            PropertyNames.dbUrl,
            PropertyNames.dbUsername,
            PropertyNames.dbPassword)
}


open class PerformanceReport : JavaExec() {

    @Input
    lateinit var resultStoreClass: String

    @OutputDirectory
    lateinit var reportDir: File

    @TaskAction
    override fun exec() {
        main = "org.gradle.performance.results.ReportGenerator"
        args = listOf(resultStoreClass, reportDir.path)
        super.exec()
    }
}


internal
fun allTestsWereSkipped(junitXmlFile: File): Boolean =
    parseXmlFile(junitXmlFile).documentElement.run {
        getAttribute("tests") == getAttribute("skipped")
    }


private
fun parseXmlFile(file: File): Document =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)


private
fun sha1StringFor(file: File) =
    HashUtil.createHash(file, "sha1").asHexString()
