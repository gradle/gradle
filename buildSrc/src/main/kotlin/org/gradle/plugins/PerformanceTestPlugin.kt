package org.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

import accessors.java
import org.gradle.api.Task
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.internal.hash.HashUtil
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.DistributedPerformanceTest
import org.gradle.testing.PerformanceTest
import org.gradle.testing.performance.generator.tasks.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val performanceExperimentCategory = "org.gradle.performance.categories.PerformanceExperiment"
private val reportGeneratorClassName = "org.gradle.performance.results.ReportGenerator"
private val urlProperty = "org.gradle.performance.db.url"
private val workerTestTaskNameProperty = "org.gradle.performance.workerTestTaskName"
private val teamCityUserNameProperty = "teamCityUsername"
private val teamCityPasswordProperty = "teamCityPassword"
private val teamCityUrlValue = "https://builds.gradle.org/"
private val yourkitProperty = "org.gradle.performance.use_yourkit"
private val honestProfilerProperty = "org.gradle.performance.honestprofiler"
private val channelProperty = "org.gradle.performance.execution.channel"
private val coordinatorBuildIdProperty = "org.gradle.performance.coordinatorBuildId"
private val performanceTestVerboseProperty = "performanceTest.verbose"
private val baselinesProperty = "org.gradle.performance.baselines"
private val buildTypeIdProperty = "org.gradle.performance.buildTypeId"
private val dbUsernameProperty = "org.gradle.performance.db.username"
private val dbPasswordProperty = "org.gradle.performance.db.password"
private val branchnameProperty = "org.gradle.performance.branchName"

private val baseLineList = listOf("1.1", "1.12", "2.0", "2.1", "2.4", "2.9", "2.12", "2.14.1", "last")
private val resultsStoreClassName = "org.gradle.performance.results.AllResultsStore"

private val performanceTestsReportDir = "performance-tests/report"
private val h2DatabaseUrl = "jdbc:h2:./build/database"
private val performanceTestScenarioListFileName = "performance-tests/scenario-list.csv"
private val performanceTestScenarioReportFileName = "/performance-tests/scenario-report.html"

class PerformanceTestPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        apply {
            plugin("java")
        }

        val performanceTestSourceSet = createPerformanceTestSourceSet()
        addConfigurationAndDependencies()
        createCheckNoIdenticalBuildFilesTask()
        configureGeneratorTasks(project)

        val prepareSamplesTask = createPrepareSamplesTask()
        createCleanSamplesTask()

        val performanceReportTask = createPerformanceReportTask(performanceTestSourceSet)

        createLocalPerformanceTestTasks(performanceTestSourceSet, prepareSamplesTask, performanceReportTask)
        createDistributedPerformanceTestTasks(performanceTestSourceSet, prepareSamplesTask, performanceReportTask)
        configureIdePlugins(performanceTestSourceSet)

    }

    private fun Project.configureIdePlugins(performanceTestSourceSet: SourceSet) {
        plugins.withType<EclipsePlugin> {
            configure<EclipseModel> {
                classpath {
                    plusConfigurations.add(configurations["performanceTestCompile"])
                    plusConfigurations.add(configurations["performanceTestRuntime"])
                }
            }
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSourceDirs.plus(performanceTestSourceSet.withConvention(GroovySourceSet::class) { groovy.srcDirs })
                    testSourceDirs.plus(performanceTestSourceSet.resources.srcDirs)
                    scopes["TEST"]!!["plus"]!!.add(configurations["performanceTestCompile"])
                    scopes["TEST"]!!["plus"]!!.add(configurations["performanceTestRuntime"])
                }
            }
        }
    }

    private fun Project.createDistributedPerformanceTestTasks(performanceSourceSet: SourceSet,
                                                              prepareSamplesTask: Task, performanceReportTask: PerformanceReport) {
        createDistributedPerformanceTestTask("distributedPerformanceTest", performanceSourceSet, prepareSamplesTask, performanceReportTask).apply {
            (options as JUnitOptions).excludeCategories(performanceExperimentCategory)
            channel = "commits"
        }
        createDistributedPerformanceTestTask("distributedPerformanceExperiment", performanceSourceSet, prepareSamplesTask, performanceReportTask).apply {
            (options as JUnitOptions).includeCategories(performanceExperimentCategory)
            channel = "experiments"
        }
        createDistributedPerformanceTestTask("distributedFullPerformanceTest", performanceSourceSet, prepareSamplesTask, performanceReportTask).apply {
            baselines = baseLineList.toString()
            checks = "none"
            channel = "historical"
        }
    }

    private fun Project.createDistributedPerformanceTestTask(name: String, performanceSourceSet: SourceSet,
                                                             prepareSamplesTask: Task, performanceReportTask: PerformanceReport): DistributedPerformanceTest {
        return tasks.create<DistributedPerformanceTest>(name) {
            configureForAnyPerformanceTestTask(this, performanceSourceSet, prepareSamplesTask, performanceReportTask)
            scenarioList = file("$buildDir/$performanceTestScenarioListFileName")
            scenarioReport = file("$buildDir$performanceTestScenarioReportFileName")
            buildTypeId = propertyOrNull(buildTypeIdProperty)
            workerTestTaskName = if (project.hasProperty(workerTestTaskNameProperty)) project.property(workerTestTaskNameProperty).toString()
                    else "fullPerformanceTest"
            coordinatorBuildId = propertyOrNull(coordinatorBuildIdProperty)
            branchName = propertyOrNull(branchnameProperty)
            teamCityUrl = teamCityUrlValue
            teamCityUsername = propertyOrNull(teamCityUserNameProperty)
            teamCityPassword = propertyOrNull(teamCityPasswordProperty)
            afterEvaluate {
                if (branchName != null && !branchName.isEmpty()) {
                    channel = channel + "-" + branchName
                }
            }
        }
    }

    private fun Project.propertyOrNull(projectPropertyName: String) : String? {
        return if (project.hasProperty(projectPropertyName)) project.property(projectPropertyName) as String else null

    }

    private fun Project.createLocalPerformanceTestTasks(performanceSourceSet: SourceSet,
                                                        prepareSamplesTask: Task, performanceReportTask: PerformanceReport) {
        (createLocalPerformanceTestTask("performanceTest", performanceSourceSet, prepareSamplesTask, performanceReportTask).options as JUnitOptions).excludeCategories(performanceExperimentCategory)

        (createLocalPerformanceTestTask("performanceExperiment", performanceSourceSet, prepareSamplesTask, performanceReportTask).options as JUnitOptions).includeCategories(performanceExperimentCategory)

        createLocalPerformanceTestTask("fullPerformanceTest", performanceSourceSet, prepareSamplesTask, performanceReportTask)

        createLocalPerformanceTestTask("performanceAdhocTest", performanceSourceSet, prepareSamplesTask, performanceReportTask).apply {
            systemProperty(urlProperty, h2DatabaseUrl)
            channel = "adhoc"
        }
    }

    private fun Project.createLocalPerformanceTestTask(name: String, performanceSourceSet: SourceSet,
                                                       prepareSamplesTask: Task, performanceReportTask: PerformanceReport): PerformanceTest {
        val task = tasks.create<PerformanceTest>(name) {
            configureForAnyPerformanceTestTask(this, performanceSourceSet, prepareSamplesTask, performanceReportTask)
            if (project.hasProperty(yourkitProperty)) {
                testLogging.showStandardStreams = true
                systemProperties[yourkitProperty] = "1"
                outputs.upToDateWhen { false }
            }
            if (project.hasProperty(honestProfilerProperty)) {
                systemProperties[honestProfilerProperty] = "1"
            }
            if (project.hasProperty(performanceTestVerboseProperty)) {
                testLogging.showStandardStreams = true
            }

            val junitXmlDir = reports.junitXml.destination
            val testResultsZipTask = tasks.create<Zip>("${name}ResultsZip") {
                from(junitXmlDir) {
                    include("**/TEST-*.xml")
                    includeEmptyDirs = false
                    eachFile {
                        try {
                            val xmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this.file)
                            val testsElement = xmlDoc.getElementsByTagName("tests").item(0)
                            val skippedElement = xmlDoc.getElementsByTagName("skipped").item(0)
                            if (testsElement.textContent == skippedElement.textContent) {
                                this.exclude()
                            }
                        } catch (e: Exception) {
                            this.exclude()
                        }
                    }
                    from(debugArtifactsDirectory)
                    destinationDir = buildDir
                    archiveName = "test-results-${junitXmlDir.name}.zip"
                }

            }
            finalizedBy(testResultsZipTask)
            val cleanTestResultsZipTask = tasks.create<Delete>("clean${testResultsZipTask.name.capitalize()}") {
                delete(testResultsZipTask.archivePath)
            }
            tasks.getByName("clean${name.capitalize()}").dependsOn(cleanTestResultsZipTask)
        }

        tasks.create<Delete>("clean${task.name.capitalize()}") {
            delete(task.outputs)
            dependsOn("clean${performanceReportTask.name.capitalize()}")
        }


        return task
    }

    private fun Project.configureForAnyPerformanceTestTask(task: PerformanceTest, performanceSourceSet: SourceSet,
                                                           prepareSamplesTask: Task, performanceReportTask: PerformanceReport) {
        task.apply {
            group = "verification"
            systemProperties(propertiesForPerformanceDb())
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath

            requiresBinZip = true
            requiresLibsRepo = true
            maxParallelForks = 1
            finalizedBy(performanceReportTask)
            if (project.hasProperty(baselinesProperty)) {
                systemProperty(baselinesProperty, project.property(baselinesProperty))
            }

            dependsOn(prepareSamplesTask)
            mustRunAfter(tasks.withType<ProjectGeneratorTask>())
            mustRunAfter(tasks.withType<RemoteProject>())
            mustRunAfter(tasks.withType<JavaExecProjectGeneratorTask>())

            doFirst {
                if (channel != null) {
                    performanceReportTask.systemProperty(channelProperty, channel)
                }
            }
        }
    }

    private fun Project.propertiesForPerformanceDb(): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        val urlProperty = urlProperty
        val url = project.findProperty(urlProperty)
        if (url != null) {
            properties.put(urlProperty, url as String)
        }
        val usernameProperty = dbUsernameProperty
        val username = project.findProperty(usernameProperty)
        if (username != null) {
            properties.put(usernameProperty, username as String)
        }
        val passwordProperty = dbPasswordProperty
        val password = project.findProperty(passwordProperty)
        if (password != null) {
            properties.put(passwordProperty, password as String)
        }
        return properties
    }

    private fun Project.createPerformanceReportTask(performanceTestSourceSet: SourceSet): PerformanceReport {
        return tasks.create<PerformanceReport>("performanceReport") {
            systemProperties(propertiesForPerformanceDb())
            classpath = performanceTestSourceSet.runtimeClasspath
            resultStoreClass = resultsStoreClassName
            reportDir = File(buildDir, performanceTestsReportDir)
            outputs.upToDateWhen { false }
        }
    }

    private fun Project.createCleanSamplesTask(): Task {
        return tasks.create<Delete>("cleanSamples") {
            delete { tasks.withType<ProjectGeneratorTask>().map { it.outputs } }
            delete { tasks.withType<RemoteProject>().map { it.outputDirectory } }
            delete { tasks.withType<JavaExecProjectGeneratorTask>().map { it.outputs } }
        }
    }

    private fun Project.createPrepareSamplesTask(): Task {
        return tasks.create("prepareSamples") {
            group = "Project Setup"
            description = "Generates all sample projects for automated performance tests"
            dependsOn(tasks.withType<ProjectGeneratorTask>())
            dependsOn(tasks.withType<RemoteProject>())
            dependsOn(tasks.withType<JavaExecProjectGeneratorTask>())
        }
    }

    private fun Project.configureGeneratorTasks(project: Project) {
        tasks.withType<ProjectGeneratorTask> {
            group = "Project setup"
        }
        tasks.withType<TemplateProjectGeneratorTask> {
            sharedTemplateDirectory = project.project(":internalPerformanceTesting").file("src/templates")
        }
        tasks.withType<AbstractProjectGeneratorTask> {
            if (project.hasProperty("maxProjects")) {
                    project.extra.set("projects", project.property("maxProjects") as Int)
            }
        }
        tasks.withType<JvmProjectGeneratorTask> {
            testDependencies = configurations["junit"]
        }
    }

    private fun Project.createCheckNoIdenticalBuildFilesTask() {
        tasks.create("checkNoIdenticalBuildFiles") {
            doLast {
                val files = mapOf<String, MutableList<File>>().withDefault { mutableListOf() }
                buildDir.walkTopDown().forEach {
                    if (it.name.endsWith(".gradle")) {
                        val hash = HashUtil.createHash(it, "sha1").asHexString()
                        files[hash]!!.add(it)
                    }
                }

                files.forEach { hash, candidates ->
                    if (candidates.size > 1) {
                        println("Duplicate build files found for hash '$hash' : $candidates")
                    }
                }
            }
        }
    }

    private fun Project.addConfigurationAndDependencies() {
        configurations {
            "performanceTestCompile"().extendsFrom(configurations["testCompile"])
            "performanceTestRuntime"().extendsFrom(configurations["testRuntime"])
            "partialDistribution"().extendsFrom(configurations["performanceTestRuntimeClasspath"])
            "junit"()
        }

        dependencies {
            "performanceTestCompile"(project(":internalPerformanceTesting"))
            "junit"("junit:junit:4.12")
        }
    }

    private fun Project.createPerformanceTestSourceSet(): SourceSet = java.sourceSets.run {
        val main by getting
        val test by getting
        val performanceTest by creating {
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
        }
        performanceTest
    }
}

open class PerformanceReport : JavaExec() {
    @Input
    lateinit var resultStoreClass: String

    @OutputDirectory
    lateinit var reportDir: File

    @TaskAction
    override fun exec() {
        main = reportGeneratorClassName
        args = listOf(resultStoreClass, reportDir.path)
        super.exec()
    }
}


