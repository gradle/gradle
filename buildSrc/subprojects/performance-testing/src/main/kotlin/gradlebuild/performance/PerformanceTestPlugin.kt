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

import gradlebuild.basics.accessors.groovy
import gradlebuild.basics.kotlindsl.selectStringProperties
import gradlebuild.basics.kotlindsl.stringPropertyOrNull
import gradlebuild.identity.extension.ModuleIdentityExtension
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.excludeCategories
import gradlebuild.integrationtests.includeCategories
import gradlebuild.performance.generator.tasks.AbstractProjectGeneratorTask
import gradlebuild.performance.generator.tasks.JavaExecProjectGeneratorTask
import gradlebuild.performance.generator.tasks.JvmProjectGeneratorTask
import gradlebuild.performance.generator.tasks.ProjectGeneratorTask
import gradlebuild.performance.generator.tasks.RemoteProject
import gradlebuild.performance.generator.tasks.TemplateProjectGeneratorTask
import gradlebuild.performance.reporter.DefaultPerformanceReporter
import gradlebuild.performance.tasks.BuildCommitDistribution
import gradlebuild.performance.tasks.DetermineBaselines
import gradlebuild.performance.tasks.DistributedPerformanceTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.performance.tasks.PerformanceTestReport
import gradlebuild.performance.tasks.RebaselinePerformanceTests
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.hash.HashUtil
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
import kotlin.reflect.KClass


object PropertyNames {
    const val dbUrl = "org.gradle.performance.db.url"
    const val dbUsername = "org.gradle.performance.db.username"
    const val dbPassword = "org.gradle.performance.db.password"

    const val workerTestTaskName = "org.gradle.performance.workerTestTaskName"
    const val performanceTestVerbose = "performanceTest.verbose"
    const val buildTypeId = "org.gradle.performance.buildTypeId"

    const val teamCityToken = "teamCityToken"
    const val baselines = "performanceBaselines"
}


object Config {

    const val performanceTestScenarioListFileName = "performance-tests/scenario-list.csv"

    const val performanceTestReportsDir = "performance-tests/report"

    const val performanceTestResultsJsonName = "perf-results.json"
    const val performanceTestResultsJson = "performance-tests/$performanceTestResultsJsonName"

    const val teamCityUrl = "https://builds.gradle.org/"
}


private
const val performanceExperimentCategory = "org.gradle.performance.categories.PerformanceExperiment"


private
const val performanceRegressionTestCategory = "org.gradle.performance.categories.PerformanceRegressionTest"


private
const val slowPerformanceRegressionTestCategory = "org.gradle.performance.categories.SlowPerformanceRegressionTest"


interface PerformanceTestExtension {
    val baselines: Property<String>
}


@Suppress("unused")
class PerformanceTestPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val performanceTestExtension = createExtension()
        createAndWireCommitDistributionTask(performanceTestExtension)

        val performanceTestSourceSet = createPerformanceTestSourceSet()
        addPerformanceTestConfigurationAndDependencies()
        createCheckNoIdenticalBuildFilesTask()
        configureGeneratorTasks()

        createCleanSamplesTask()

        createLocalPerformanceTestTasks(performanceTestSourceSet)
        createDistributedPerformanceTestTasks(performanceTestSourceSet)

        createRebaselineTask(performanceTestSourceSet)

        configureIdePlugins(performanceTestSourceSet)
    }

    private
    fun Project.createExtension(): PerformanceTestExtension {
        val performanceTestExtension = extensions.create<PerformanceTestExtension>("performanceTest")
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
    fun Project.createCheckNoIdenticalBuildFilesTask() {
        tasks.register("checkNoIdenticalBuildFiles") {
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

                filesBySha1.forEach { (hash, candidates) ->
                    if (candidates.size > 1) {
                        logger.lifecycle("Duplicate build files found for hash '$hash' : $candidates")
                    }
                }
            }
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
    fun Project.configureTestProjectGenerators(action: TaskCollection<*>.() -> Unit) {
        tasks.withType<ProjectGeneratorTask>().action()
        tasks.withType<RemoteProject>().action()
        tasks.withType<JavaExecProjectGeneratorTask>().action()
        tasks.withType<Copy>().matching { it.name in setOf("archivePerformanceProject", "workerApiProject", "generateLotsOfDeprecationWarnings") }.action()
    }


    private
    fun Project.createCleanSamplesTask() =
        tasks.register("cleanSamples", Delete::class) {
            configureTestProjectGenerators {
                this@register.delete(provider { map { it.outputs } })
            }
        }

    private
    fun Project.createPerformanceReporter() =
        objects.newInstance(DefaultPerformanceReporter::class).also {
            it.projectName = name
            it.reportGeneratorClass = "org.gradle.performance.results.report.DefaultReportGenerator"
            it.commitId = the<ModuleIdentityExtension>().gradleBuildCommitId.get()
        }

    private
    fun Project.createLocalPerformanceTestTasks(performanceSourceSet: SourceSet) {

        fun create(name: String, configure: PerformanceTest.() -> Unit = {}) =
            createLocalPerformanceTestTask(name, performanceSourceSet).also {
                it.configure(configure)
            }

        create("performanceTest") {
            includeCategories(performanceRegressionTestCategory)
            excludeCategories(slowPerformanceRegressionTestCategory)
        }

        create("slowPerformanceTest") {
            includeCategories(slowPerformanceRegressionTestCategory)
        }

        create("performanceExperiment") {
            includeCategories(performanceExperimentCategory)
        }

        create("fullPerformanceTest")

        create("performanceAdhocTest") {
            performanceReporter = createPerformanceReporter()
            channel = "adhoc"
            outputs.doNotCacheIf("Is adhoc performance test") { true }
        }

        val shouldLoadScenariosFromFile = project.providers.gradleProperty("includePerformanceTestScenarios")
            .forUseAtConfigurationTime()
            .getOrElse("false") == "true"

        configureTestProjectGenerators {
            all {
                val sampleGenerator = this
                val testProject = sampleGenerator.name
                create("${testProject}PerformanceAdHocTest") {
                    description = "Runs ad-hoc performance tests on $testProject - can be used locally"
                    performanceReporter = createPerformanceReporter()
                    channel = "adhoc"
                    outputs.doNotCacheIf("Is adhoc performance test") { true }
                    setTestProjectGenerationTask(sampleGenerator)
                }
                val channelSuffix = if (OperatingSystem.current().isLinux) "" else "-${OperatingSystem.current().familyName.toLowerCase()}"
                create("${testProject}PerformanceTest") {
                    description = "Runs performance tests on $testProject - supposed to be used on CI"
                    performanceReporter = createPerformanceReporter()
                    channel = "commits$channelSuffix"
                    setTestProjectGenerationTask(sampleGenerator)

                    retry {
                        maxRetries.set(1)
                    }

                    if (shouldLoadScenariosFromFile) {
                        val scenariosFromFile = loadScenariosFromFile(testProject)
                        if (scenariosFromFile.isNotEmpty()) {
                            setTestNameIncludePatterns(scenariosFromFile)
                        }
                        doFirst {
                            assert((filter as DefaultTestFilter).includePatterns.isNotEmpty()) { "Running $name requires to add a test filter" }
                        }
                    }
                }
            }
        }

        tasks.register<PerformanceTestReport>("performanceTestReport") {
            reportGeneratorClass.set("org.gradle.performance.results.report.DefaultReportGenerator")
        }

        tasks.register<PerformanceTestReport>("performanceTestFlakinessReport") {
            reportGeneratorClass.set("org.gradle.performance.results.report.FlakinessReportGenerator")
        }

        tasks.register<JavaExec>("writePerformanceTimes") {
            classpath(performanceSourceSet.runtimeClasspath)
            mainClass.set("org.gradle.performance.results.PerformanceTestRuntimesGenerator")
            systemProperties(project.propertiesForPerformanceDb())
            args(project.rootProject.file(".teamcity/performance-test-runtimes.json").absolutePath)
            // Never up-to-date since it reads data from the database.
            outputs.upToDateWhen { false }
        }

        tasks.withType<PerformanceTestReport>().configureEach {
            classpath.from(performanceSourceSet.runtimeClasspath)
            performanceResultsDirectory.set(project.rootProject.file("perf-results"))
            reportDir.set(project.layout.buildDirectory.dir(this@configureEach.name))
            databaseParameters.set(project.propertiesForPerformanceDb())
            val moduleIdentity = project.the<ModuleIdentityExtension>()
            branchName.set(moduleIdentity.gradleBuildBranch)
            channel.convention(branchName.map { "commits-$it" })
            commitId.set(moduleIdentity.gradleBuildCommitId)
            projectName.set(project.name)
        }
    }

    private
    fun Project.loadScenariosFromFile(testProject: String): List<String> {
        val scenarioFile = project.rootProject.file("performance-test-splits/include-$testProject-performance-scenarios.csv")
        return if (scenarioFile.isFile)
            scenarioFile.readLines(StandardCharsets.UTF_8)
                .filter { it.isNotEmpty() }
                .map {
                    val (className, scenario) = it.split(";")
                    "$className.$scenario"
                }
        else listOf()
    }

    private
    fun Project.createDistributedPerformanceTestTasks(performanceSourceSet: SourceSet) {

        fun create(name: String, clazz: KClass<out DistributedPerformanceTest>, configure: DistributedPerformanceTest.() -> Unit = {}) {
            createDistributedPerformanceTestTask(name, clazz, performanceSourceSet).configure(configure)
        }

        val channelSuffix = if (OperatingSystem.current().isLinux) "" else "-${OperatingSystem.current().familyName.toLowerCase()}"

        create("distributedPerformanceTest", DistributedPerformanceTest::class) {
            includeCategories(performanceRegressionTestCategory)
            excludeCategories(slowPerformanceRegressionTestCategory)
            channel = "commits$channelSuffix"
            retryFailedScenarios()
        }
        create("distributedSlowPerformanceTest", DistributedPerformanceTest::class) {
            includeCategories(slowPerformanceRegressionTestCategory)
            channel = "commits$channelSuffix"
            retryFailedScenarios()
        }
        create("distributedPerformanceExperiment", DistributedPerformanceTest::class) {
            includeCategories(performanceExperimentCategory)
            channel = "experiments$channelSuffix"
            retryFailedScenarios()
        }
        create("distributedHistoricalPerformanceTest", DistributedPerformanceTest::class) {
            excludeCategories(performanceExperimentCategory)
            checks = "none"
            channel = "historical$channelSuffix"
        }
        create("distributedFlakinessDetection", DistributedPerformanceTest::class) {
            includeCategories(performanceRegressionTestCategory)
            distributedPerformanceReporter.reportGeneratorClass = "org.gradle.performance.results.report.FlakinessReportGenerator"
            repeatScenarios(3)
            checks = "none"
            channel = "flakiness-detection$channelSuffix"
        }
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
    fun Project.createDistributedPerformanceTestTask(
        name: String,
        clazz: KClass<out DistributedPerformanceTest>,
        performanceSourceSet: SourceSet
    ): TaskProvider<out DistributedPerformanceTest> {
        val performanceTest = tasks.register(name, clazz) {
            configureForAnyPerformanceTestTask(this, performanceSourceSet)
            scenarioList = layout.buildDirectory.file("$name/${Config.performanceTestScenarioListFileName}").get().asFile
            buildTypeId = stringPropertyOrNull(PropertyNames.buildTypeId)
            workerTestTaskName = stringPropertyOrNull(PropertyNames.workerTestTaskName) ?: "fullPerformanceTest"
            teamCityUrl = Config.teamCityUrl
            teamCityToken = stringPropertyOrNull(PropertyNames.teamCityToken)
            distributedPerformanceReporter = createPerformanceReporter()
        }

        afterEvaluate {
            performanceTest.configure {
                branchName?.takeIf { it.isNotEmpty() }?.let { branchName ->
                    channel = "$channel-$branchName"
                }
            }
        }

        return performanceTest
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

    private
    fun Project.createLocalPerformanceTestTask(
        name: String,
        performanceSourceSet: SourceSet
    ): TaskProvider<out PerformanceTest> {
        val performanceTest = tasks.register(name, PerformanceTest::class) {
            configureForAnyPerformanceTestTask(this, performanceSourceSet)

            if (project.hasProperty(PropertyNames.performanceTestVerbose)) {
                testLogging.showStandardStreams = true
            }
        }
        val testResultsZipTask = testResultsZipTaskFor(performanceTest, name)

        performanceTest.configure {
            finalizedBy(testResultsZipTask)
        }

        tasks.named("clean${name.capitalize()}") {
            delete(performanceTest)
            dependsOn(testResultsZipTask.map { "clean${it.name.capitalize()}" }) // Avoid realizing because of issue
        }

        return performanceTest
    }

    private
    fun Project.testResultsZipTaskFor(performanceTest: TaskProvider<out PerformanceTest>, name: String): TaskProvider<Zip> =
        tasks.register("${name}ResultsZip", Zip::class) {
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
                rename(Config.performanceTestResultsJsonName, "perf-results-$name.json")
            }
            destinationDirectory.set(buildDir)
            archiveFileName.set("test-results-${junitXmlDir.name}.zip")
        }

    private
    fun Project.configureForAnyPerformanceTestTask(
        task: PerformanceTest,
        performanceSourceSet: SourceSet
    ) {
        task.apply {
            group = "verification"
            buildId = System.getenv("BUILD_ID")
            reportDir = layout.buildDirectory.file("${task.name}/${Config.performanceTestReportsDir}").get().asFile
            resultsJson = layout.buildDirectory.file("${task.name}/${Config.performanceTestResultsJson}").get().asFile
            addDatabaseParameters(propertiesForPerformanceDb())
            testClassesDirs = performanceSourceSet.output.classesDirs
            classpath = performanceSourceSet.runtimeClasspath

            branchName = project.the<ModuleIdentityExtension>().gradleBuildBranch.get()

            maxParallelForks = 1

            useJUnitPlatform()

            jvmArgs("-Xmx5g", "-XX:+HeapDumpOnOutOfMemoryError")

            registerTemplateInputsToPerformanceTest()

            configureTestProjectGenerators {
                this@apply.mustRunAfter(this)
            }
            configureGitInfo()

            retry {
                maxRetries.set(0)
            }
        }
    }

    private
    fun PerformanceTest.registerTemplateInputsToPerformanceTest() {
        val registerInputs: (Task) -> Unit = { prepareSampleTask ->
            val prepareSampleTaskInputs = prepareSampleTask.inputs.properties.mapKeys { entry -> "${prepareSampleTask.name}_${entry.key}" }
            prepareSampleTaskInputs.forEach { key, value ->
                inputs.property(key, value).optional(true)
            }
        }
        project.configureTestProjectGenerators {
            // TODO: Remove this hack https://github.com/gradle/gradle-native/issues/864
            (project.tasks as DefaultTaskContainer).mutationGuard.withMutationEnabled<DefaultTaskContainer> {
                all(registerInputs)
            }
        }
    }

    private
    fun Project.propertiesForPerformanceDb(): Map<String, String> =
        selectStringProperties(
            PropertyNames.dbUrl,
            PropertyNames.dbUsername,
            PropertyNames.dbPassword)

    private
    fun PerformanceTest.configureGitInfo() {
        systemProperty("gradleBuildBranch", project.the<ModuleIdentityExtension>().gradleBuildBranch.get())
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
