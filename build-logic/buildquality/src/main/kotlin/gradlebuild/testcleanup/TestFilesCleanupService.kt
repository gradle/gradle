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

package gradlebuild.testcleanup

import gradlebuild.classycle.tasks.Classycle
import gradlebuild.cleanup.tasks.KillLeakingJavaProcesses
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.reporting.Reporting
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject


abstract class TestFilesCleanupService @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BuildService<TestFilesCleanupService.Params>, AutoCloseable, OperationCompletionListener {
    interface Params : BuildServiceParameters {
        /**
         * Key is the path of a task, value is the possible report dirs it generates.
         */
        val taskPathToGenericHtmlReports: MapProperty<String, List<File>>
        val taskPathToAttachedReports: MapProperty<String, List<File>>
        val taskPathToCustomReports: MapProperty<String, List<File>>
        val taskPathToTDTraceJsons: MapProperty<String, List<File>>

        /**
         * Key is the path of the test, value is Test.binaryResultsDir
         */
        val testPathToBinaryResultsDirs: MapProperty<String, File>
        val buildDir: Property<File>
        val rootBuildDir: Property<File>
        val reportOnly: Property<Boolean>
        val cleanupRunnerStep: Property<Boolean>
        val currentProjectName: Property<String>
    }

    companion object {
        fun register(currentProject: Project, reportOnly: Property<Boolean>, buildEventsListenerRegistry: BuildEventsListenerRegistry) {
            currentProject.gradle.taskGraph.whenReady {
                val testFilesCleanupService = currentProject.gradle.sharedServices.registerIfAbsent("testFilesCleanup${currentProject.name}", TestFilesCleanupService::class.java) {
                    val scheduledTasks = this@whenReady.allTasks.filter { it.project == currentProject }
                    val isCleanupRunnerStep = this@whenReady.allTasks.filterIsInstance<KillLeakingJavaProcesses>().isNotEmpty()
                    parameters.taskPathToGenericHtmlReports.putAll(scheduledTasks.associate { it.path to it.failedTaskGenericHtmlReports() }.filter { it.value.isNotEmpty() })
                    parameters.taskPathToAttachedReports.putAll(scheduledTasks.associate { it.path to it.attachedReportLocations() }.filter { it.value.isNotEmpty() })
                    parameters.taskPathToCustomReports.putAll(scheduledTasks.associate { it.path to it.customReports() }.filter { it.value.isNotEmpty() })
                    parameters.taskPathToTDTraceJsons.putAll(scheduledTasks.associate { it.path to it.findTraceJson() }.filter { it.value.isNotEmpty() })

                    parameters.testPathToBinaryResultsDirs.putAll(
                        scheduledTasks.filterIsInstance<Test>().associate { it.path to it.binaryResultsDirectory.get().asFile }
                    )

                    parameters.buildDir.set(currentProject.buildDir)
                    parameters.rootBuildDir.set(currentProject.rootProject.buildDir)
                    parameters.cleanupRunnerStep.set(isCleanupRunnerStep)
                    parameters.reportOnly.set(reportOnly.get())
                    parameters.currentProjectName.set(currentProject.name)
                }
                buildEventsListenerRegistry.onTaskCompletion(testFilesCleanupService)
            }
        }

        private
        fun Task.findTraceJson(): List<File> {
            if (this !is Test) {
                return emptyList()
            }
            // build/test-results/embeddedIntegTest/trace.json
            val traceJson = project.buildDir.resolve("test-results/$name/trace.json")
            return if (traceJson.isFile) {
                listOf(traceJson)
            } else {
                emptyList()
            }
        }

        private
        fun Task.failedTaskGenericHtmlReports() = when (this) {
            is Reporting<*> -> listOf(this.reports["html"].outputLocation.get().asFile)
            else -> emptyList()
        }

        private
        fun Task.customReports(): List<File> = when (this) {
            is ValidatePlugins -> listOf(outputFile.get().asFile)
            is Classycle -> listOf(reportFile.get().asFile)
            is FindBrokenInternalLinks -> listOf(reportFile.get().asFile)
            is DistributionTest -> listOf(
                gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile,
                gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile,
                gradleInstallationForTest.daemonRegistry.get().asFile
            )
            is GenerateReportsTask -> listOf(reportsOutputDirectory.get().asFile)
            else -> emptyList()
        }

        private
        fun Task.attachedReportLocations() = when (this) {
            is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName))
            is PerformanceTest -> listOf(reportDir.parentFile)
            else -> emptyList()
        }
    }

    private
    val relevantTasks: Set<String> = mutableSetOf<String>().apply {
        addAll(parameters.taskPathToAttachedReports.get().keys)
        addAll(parameters.taskPathToCustomReports.get().keys)
        addAll(parameters.taskPathToGenericHtmlReports.get().keys)
        addAll(parameters.taskPathToTDTraceJsons.get().keys)
        addAll(parameters.testPathToBinaryResultsDirs.get().keys)
    }.toSet()

    private
    val failedTaskPaths: MutableList<String> = mutableListOf()

    private
    val executedTaskPaths: MutableList<String> = mutableListOf()

    private
    val taskPathToGenericHtmlReports: Map<String, List<File>>
        get() = parameters.taskPathToGenericHtmlReports.get()

    private
    val taskPathToAttachedReports: Map<String, List<File>>
        get() = parameters.taskPathToAttachedReports.get()

    private
    val taskPathToCustomReports: Map<String, List<File>>
        get() = parameters.taskPathToCustomReports.get()

    private
    val taskPathToTDTraceJsons: Map<String, List<File>>
        get() = parameters.taskPathToTDTraceJsons.get()

    private
    val testPathToBinaryResultsDirs: Map<String, File>
        get() = parameters.testPathToBinaryResultsDirs.get()

    private
    val buildDir: File
        get() = parameters.buildDir.get()

    private
    val rootBuildDir: File
        get() = parameters.rootBuildDir.get()

    private
    val reportOnly: Boolean
        get() = parameters.reportOnly.get()

    private
    val cleanupRunnerStep: Boolean
        get() = parameters.cleanupRunnerStep.get()

    private
    val currentProjectName: String
        get() = parameters.currentProjectName.get()

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent && relevantTasks.contains(event.descriptor.taskPath)) {
            val taskPath = event.descriptor.taskPath
            when (event.result) {
                is TaskSuccessResult -> {
                    executedTaskPaths.add(taskPath)
                    testPathToBinaryResultsDirs[taskPath]?.apply {
                        if (containsFailedTest(this)) {
                            failedTaskPaths.add(taskPath)
                        }
                    }
                }
                is TaskFailureResult -> {
                    executedTaskPaths.add(taskPath)
                    failedTaskPaths.add(taskPath)
                }
                else -> {
                }
            }
        }
    }

    override fun close() {
        val tmpTestFiles = tmpTestFiles(buildDir)
        prepareReportsForCiPublishing(
            if (tmpTestFiles.isEmpty()) failedTaskPaths else executedTaskPaths,
            executedTaskPaths,
            tmpTestFiles.keys
        )
        cleanUp(tmpTestFiles.keys)
        if (!cleanupRunnerStep) {
            verifyTestFilesCleanup(failedTaskPaths, tmpTestFiles)
        }
    }

    private
    fun String.isPathForTestTask() = testPathToBinaryResultsDirs.containsKey(this)

    private
    fun verifyTestFilesCleanup(failedTaskPaths: List<String>, tmpTestFiles: Map<File, List<String>>) {
        if (failedTaskPaths.any { it.isPathForTestTask() }) {
            println("Leftover files: $tmpTestFiles")
            return
        }

        if (tmpTestFiles.isNotEmpty()) {
            val nonEmptyDirs = tmpTestFiles.entries.joinToString("\n") { (dir, relativePaths) ->
                "${dir.absolutePath}:\n ${relativePaths.joinToString("\n ")}"
            }
            val errorMessage = "Found non-empty test files dir:\n$nonEmptyDirs"
            if (reportOnly) {
                println(errorMessage)
            } else {
                throw GradleException(errorMessage)
            }
        }
    }

    /**
     * Returns non-empty directories: the mapping of directory to at most 4 leftover files' relative path in the directory.
     */
    private
    fun tmpTestFiles(projectBuildDirectory: File): Map<File, List<String>> = projectBuildDirectory.resolve("tmp/test files")
        .listFiles()
        ?.associateWith { dir ->
            val dirPath = dir.toPath()
            Files.walk(dirPath).use { paths ->
                paths.filter { !it.toFile().isDirectory }
                    .limit(4)
                    .map { dirPath.relativize(it).toString() }
                    .collect(Collectors.toList())
            }
        }?.filter {
            it.value.isNotEmpty()
        } ?: emptyMap()

    private
    fun prepareReportsForCiPublishing(taskPathsToCollectReports: List<String>, executedTaskPaths: List<String>, tmpTestFiles: Collection<File>) {
        val collectedTaskHtmlReports = taskPathsToCollectReports.flatMap { taskPathToGenericHtmlReports.getOrDefault(it, emptyList()) }
        val attachedReports = executedTaskPaths.flatMap { taskPathToAttachedReports.getOrDefault(it, emptyList()) }
        val executedTaskCustomReports = taskPathsToCollectReports.flatMap { taskPathToCustomReports.getOrDefault(it, emptyList()) }
        val testDistributionTraceJsons = executedTaskPaths.flatMap { taskPathToTDTraceJsons.getOrDefault(it, emptyList()) }
        val allReports = collectedTaskHtmlReports + attachedReports + executedTaskCustomReports + tmpTestFiles + testDistributionTraceJsons
        allReports.forEach { report ->
            prepareReportForCiPublishing(report)
        }
    }

    private
    fun prepareReportForCiPublishing(report: File) {
        if (report.exists()) {
            if (report.isDirectory) {
                val destFile = rootBuildDir.resolve("report-$currentProjectName-${report.name}.zip")
                zip(destFile, report)
            } else {
                fileSystemOperations.copy {
                    from(report)
                    into(rootBuildDir)
                    rename { "report-$currentProjectName-${report.parentFile.name}-${report.name}" }
                }
            }
        }
    }

    private
    fun zip(destZip: File, srcDir: File) {
        destZip.parentFile.mkdirs()
        ZipOutputStream(FileOutputStream(destZip), StandardCharsets.UTF_8).use { zipOutput ->
            val srcPath = srcDir.toPath()
            Files.walk(srcPath).use { paths ->
                paths
                    .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                    .forEach { path ->
                        val zipEntry = ZipEntry(srcPath.relativize(path).toString())
                        zipOutput.putNextEntry(zipEntry)
                        Files.copy(path, zipOutput)
                        zipOutput.closeEntry()
                    }
            }
        }
    }

    // We count the test task containing flaky result as failed
    private
    fun containsFailedTest(testBinaryResultDir: File): Boolean {
        if (this !is Test) {
            return false
        }

        var containingFailures = false

        val serializer = TestResultSerializer(testBinaryResultDir)
        if (serializer.isHasResults) {
            serializer.read {
                if (failuresCount > 0) {
                    containingFailures = true
                }
            }
        }
        return containingFailures
    }

    /**
     * After archiving the test files, do a cleanup to get rid of TeamCity "XX published a lot of small artifacts" warning.
     */
    private
    fun cleanUp(filesToCleanUp: Collection<File>) {
        try {
            fileSystemOperations.delete {
                delete(*filesToCleanUp.toTypedArray())
            }
        } catch (e: Exception) {
            // https://github.com/gradle/gradle-private/issues/2983#issuecomment-596083202
            e.printStackTrace()
        }
    }
}
