/*
 * Copyright 2022 the original author or authors.
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
import java.io.IOException
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
import kotlin.streams.toList


typealias LeftoverFiles = Map<File, List<String>>


abstract class TestFilesCleanupService @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BuildService<TestFilesCleanupService.Params>, AutoCloseable, OperationCompletionListener {

    interface Params : BuildServiceParameters {
        val projectStates: MapProperty<String, TestFilesCleanupProjectState>
        val rootBuildDir: DirectoryProperty

        /**
         * Key is the path of a task, value is the possible report dirs it generates.
         */
        val taskPathToReports: MapProperty<String, List<File>>

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
    val taskPathReports: Map<String, List<File>>
        get() = parameters.taskPathToReports.get()

    private
    val rootBuildDir: File
        get() = parameters.rootBuildDir.get().asFile

    private
    val testPathToBinaryResultsDirs: Map<String, File>
        get() = parameters.testPathToBinaryResultsDirs.get()

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent && taskPathReports.containsKey(event.descriptor.taskPath)) {
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
        projectPathToExecutedTaskPaths.computeIfAbsent(taskPathToProjectPath(taskPath)) { mutableListOf() }.add(taskPath)
    }

    private
    fun addFailedTaskPath(taskPath: String) {
        projectPathToFailedTaskPaths.computeIfAbsent(taskPathToProjectPath(taskPath)) { mutableListOf() }.add(taskPath)
    }

    private
    fun getFailedTaskPaths(projectPath: String) = projectPathToFailedTaskPaths.getOrDefault(projectPath, emptyList())

    private
    fun getExecutedTaskPaths(projectPath: String) = projectPathToExecutedTaskPaths.getOrDefault(projectPath, emptyList())

    override fun close() {
        val projectPathToLeftoverFiles = mutableMapOf<String, LeftoverFiles>()
        // First run: delete any temporary directories used to extract resources from jars
        parameters.projectStates.get().values.forEach { projectExtension ->
            cleanUp(projectExtension.tmpExtractedResourcesDirs())
        }

        // Second run: collect and archive leftover files
        parameters.projectStates.get().forEach { (projectPath: String, projectExtension: TestFileCleanUpExtension) ->
            val tmpTestFiles = projectExtension.tmpTestFiles()

            projectExtension.prepareReportsForCiPublishing(
                getExecutedTaskPaths(projectPath),
                tmpTestFiles.keys
            )
            cleanUp(tmpTestFiles.keys)
            projectPathToLeftoverFiles[projectPath] = tmpTestFiles
        }

        // Third run: verify and throw exceptions
        val exceptions = mutableListOf<Exception>()
        parameters.projectStates.get()
            .filter { projectPathToLeftoverFiles.containsKey(it.key) }
            .forEach { (projectPath: String, projectExtension: TestFileCleanUpExtension) ->
                try {
                    projectExtension.verifyTestFilesCleanup(projectPath, projectPathToLeftoverFiles.getValue(projectPath))
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

    private
    fun isPathForTestTask(taskPath: String) = testPathToBinaryResultsDirs.containsKey(taskPath)

    private
    fun isAnyTestTaskFailed(projectPath: String) = getFailedTaskPaths(projectPath).any { isPathForTestTask(it) }

    private
    fun TestFileCleanUpExtension.verifyTestFilesCleanup(projectPath: String, tmpTestFiles: Map<File, List<String>>) {
        if (isAnyTestTaskFailed(projectPath)) {
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
    fun TestFilesCleanupProjectState.tmpTestFiles(): LeftoverFiles = projectBuildDir.get().asFile.resolve("tmp/teŝt files")
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

    /**
     * Returns any temporary directories used to extract resources from jars.
     *
     * These directories will be created as siblings of the randomly assigned test root directories, with the fixed name {@code tmp-extracted-resources}.
     */
    private
    fun TestFilesCleanupProjectState.tmpExtractedResourcesDirs() = projectBuildDir.get().asFile.resolve("tmp/teŝt files")
        .listFiles()
        ?.filter { it.isDirectory }
        ?.map { it.resolve("tmp-extracted-resources") }
        ?.filter { it.exists() }
        .orEmpty()

    private
    fun TestFilesCleanupProjectState.prepareReportsForCiPublishing(executedTaskPaths: List<String>, tmpTestFiles: Collection<File>) {
        val reports = executedTaskPaths
            .flatMap { taskPathReports.getOrDefault(it, emptyList()) }
        if (isAnyTestTaskFailed(projectPath.get())) {
            prepareReportForCiPublishing(tmpTestFiles + reports)
        } else {
            prepareReportForCiPublishing(reports)
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

    private
    fun TestFilesCleanupProjectState.prepareReportForCiPublishing(reports: List<File>) {
        val projectPathName = projectPath.get().replace(':', '-')
        val projectBuildDirPath = projectBuildDir.asFile.get().toPath()

        reports.filter { it.isDirectory }.forEach {
            val destFile = rootBuildDir.resolve("report$projectPathName-${it.name}.zip")
            zip(destFile, it)
        }

        // Zip all files in project build directory into a single zip file to avoid publishing too many tiny files
        reports.filter { it.isFile && it.toPath().startsWith(projectBuildDirPath) }
            .map { projectBuildDirPath.relativize(it.toPath()).toString() to it }
            .apply { zip(rootBuildDir.resolve("report$projectPathName.zip"), this) }

        reports.filter { it.isFile && !it.toPath().startsWith(projectBuildDirPath) }
            .forEach { report ->
                fileSystemOperations.copy {
                    from(report)
                    into(rootBuildDir)
                    rename { "report$projectPathName-${report.parentFile.name}-${report.name}" }
                }
            }
    }

    /**
     * Zip a list of files with same root directory to a zip file.
     *
     * @param destZip the target zip file
     * @param srcFiles the mapping of relative path to the file
     */
    private
    fun zip(destZip: File, srcFiles: List<Pair<String, File>>) {
        if (srcFiles.isEmpty()) {
            return
        }
        destZip.parentFile.mkdirs()
        ZipOutputStream(FileOutputStream(destZip), StandardCharsets.UTF_8).use { zipOutput ->
            srcFiles.forEach { (relativePath: String, file: File) ->
                val zipEntry = ZipEntry(relativePath)
                try {
                    zipOutput.putNextEntry(zipEntry)
                    Files.copy(file.toPath(), zipOutput)
                    zipOutput.closeEntry()
                } catch (e: IOException) {
                    throw GradleException("Error copying file contents to zip. File: " + file.toPath(), e)
                }
            }
        }
    }

    private
    fun zip(destZip: File, srcDir: File) {
        val srcPath = srcDir.toPath()
        Files.walk(srcPath).use { paths ->
            zip(destZip,
                paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                    .map { srcPath.relativize(it).toString() to it.toFile() }
                    .toList()
            )
        }
    }

    private
    fun taskPathToProjectPath(taskPath: String): String {
        return taskPath.substringBeforeLast(":").ifEmpty { ":" }
    }
}
