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

import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import gradlebuild.testcleanup.extension.TestFilesCleanupProjectState
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


typealias LeftoverFiles = Map<File, List<String>>


abstract class TestFilesCleanupService @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BuildService<TestFilesCleanupService.Params>, AutoCloseable, OperationCompletionListener {

    interface Params : BuildServiceParameters {
        val projectStates: MapProperty<String, TestFilesCleanupProjectState>
        val rootBuildDir: DirectoryProperty
        val cleanupRunnerStep: Property<Boolean>

        /**
         * The mapping task path to project path
         */
        val relevantTaskPathToProjectPath: MapProperty<String, String>

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
    }

    private
    val projectPathToFailedTaskPaths: MutableMap<String, MutableList<String>> = mutableMapOf()

    private
    val projectPathToExecutedTaskPaths: MutableMap<String, MutableList<String>> = mutableMapOf()

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
    val rootBuildDir: File
        get() = parameters.rootBuildDir.get().asFile

    private
    val relevantTaskPathToProjectPath: Map<String, String>
        get() = parameters.relevantTaskPathToProjectPath.get()

    private
    val cleanupRunnerStep: Boolean
        get() = parameters.cleanupRunnerStep.get()

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent && relevantTaskPathToProjectPath.containsKey(event.descriptor.taskPath)) {
            val taskPath = event.descriptor.taskPath
            when (event.result) {
                is TaskSuccessResult -> {
                    addExecutedTaskPath(taskPath)
                    if (containsFailedTest(taskPath)) {
                        addFailedTaskPath(taskPath)
                    }
                }
                is TaskFailureResult -> {
                    addExecutedTaskPath(taskPath)
                    addFailedTaskPath(taskPath)
                }
                else -> {
                }
            }
        }
    }

    private
    fun containsFailedTest(taskPath: String): Boolean {
        return testPathToBinaryResultsDirs[taskPath]?.let { containsFailedTest(it) } == true
    }

    private
    fun addExecutedTaskPath(taskPath: String) {
        projectPathToExecutedTaskPaths.computeIfAbsent(relevantTaskPathToProjectPath.getValue(taskPath)) { mutableListOf() }.add(taskPath)
    }

    private
    fun addFailedTaskPath(taskPath: String) {
        projectPathToFailedTaskPaths.computeIfAbsent(relevantTaskPathToProjectPath.getValue(taskPath)) { mutableListOf() }.add(taskPath)
    }

    private
    fun getFailedTaskPaths(projectPath: String) = projectPathToFailedTaskPaths.getOrDefault(projectPath, emptyList())

    private
    fun getExecutedTaskPaths(projectPath: String) = projectPathToExecutedTaskPaths.getOrDefault(projectPath, emptyList())

    override fun close() {
        val projectPathToLeftoverFiles = mutableMapOf<String, LeftoverFiles>()
        // First run: collect and archieve leftover files
        parameters.projectStates.get().forEach { (projectPath: String, projectExtension: TestFileCleanUpExtension) ->
            val tmpTestFiles = projectExtension.tmpTestFiles()

            projectExtension.prepareReportsForCiPublishing(
                if (tmpTestFiles.isEmpty()) getFailedTaskPaths(projectPath) else getExecutedTaskPaths(projectPath),
                getExecutedTaskPaths(projectPath),
                tmpTestFiles.keys
            )
            cleanUp(tmpTestFiles.keys)
            projectPathToLeftoverFiles[projectPath] = tmpTestFiles
        }

        // Second run: verify and throw exceptions
        if (!cleanupRunnerStep) {
            val exceptions = mutableListOf<Exception>()
            parameters.projectStates.get()
                .filter { projectPathToLeftoverFiles.containsKey(it.key) }
                .forEach { (projectPath: String, projectExtension: TestFileCleanUpExtension) ->
                    try {
                        projectExtension.verifyTestFilesCleanup(getFailedTaskPaths(projectPath), projectPathToLeftoverFiles.getValue(projectPath))
                    } catch (e: Exception) {
                        exceptions.add(e)
                    }
                }
            when {
                exceptions.size == 1 -> throw exceptions.first()
                exceptions.isNotEmpty() -> throw DefaultMultiCauseException("Test files cleanup verification failed", exceptions)
                else -> {
                }
            }
        }
    }

    private
    fun isPathForTestTask(taskPath: String) = testPathToBinaryResultsDirs.containsKey(taskPath)

    private
    fun TestFileCleanUpExtension.verifyTestFilesCleanup(failedTaskPaths: List<String>, tmpTestFiles: Map<File, List<String>>) {
        if (failedTaskPaths.any { isPathForTestTask(it) }) {
            println("Leftover files: $tmpTestFiles")
            return
        }

        if (tmpTestFiles.isNotEmpty()) {
            val nonEmptyDirs = tmpTestFiles.entries.joinToString("\n") { (dir, relativePaths) ->
                "${dir.absolutePath}:\n ${relativePaths.joinToString("\n ")}"
            }
            val errorMessage = "Found non-empty test files dir:\n$nonEmptyDirs"
            if (reportOnly.get()) {
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
    fun TestFilesCleanupProjectState.tmpTestFiles(): LeftoverFiles = projectBuildDir.get().asFile.resolve("tmp/test files")
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
    fun TestFilesCleanupProjectState.prepareReportsForCiPublishing(taskPathsToCollectReports: List<String>, executedTaskPaths: List<String>, tmpTestFiles: Collection<File>) {
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
    fun TestFilesCleanupProjectState.prepareReportForCiPublishing(report: File) {
        if (report.exists()) {
            if (report.isDirectory) {
                val destFile = rootBuildDir.resolve("report-${projectName.get()}-${report.name}.zip")
                zip(destFile, report)
            } else {
                fileSystemOperations.copy {
                    from(report)
                    into(rootBuildDir)
                    rename { "report-${projectName.get()}-${report.parentFile.name}-${report.name}" }
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
    fun containsFailedTest(testBinaryResultsDir: File): Boolean {
        var containingFailures = false

        val serializer = TestResultSerializer(testBinaryResultsDir)
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


