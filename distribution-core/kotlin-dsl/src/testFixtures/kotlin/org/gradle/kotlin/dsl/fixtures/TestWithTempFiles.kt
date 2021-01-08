package org.gradle.kotlin.dsl.fixtures

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

import org.gradle.kotlin.dsl.support.zipTo

import org.junit.Rule

import java.io.File


@CleanupTestDirectory(fieldName = "tempFolder")
abstract class TestWithTempFiles {

    @JvmField
    @Rule
    val tempFolder = TestNameTestDirectoryProvider(javaClass)

    protected
    val root: File
        get() = tempFolder.testDirectory

    /**
     * See [org.junit.rules.TemporaryFolder.newFolder]
     */
    fun newFolder(): File =
        tempFolder.testDirectory.createDir()

    protected
    fun file(fileName: String): File =
        tempFolder.testDirectory.file(fileName)

    protected
    fun newFile(fileName: String): File =
        tempFolder.testDirectory.createFile(fileName)

    protected
    fun newFile(fileName: String, text: String): File =
        newFile(fileName).apply { writeText(text) }

    protected
    fun newFolder(vararg folderNames: String): File =
        tempFolder.testDirectory.createDir(folderNames.joinToString(File.separator))

    protected
    fun withZip(fileName: String, entries: Sequence<Pair<String, ByteArray>>): File =
        newFile(fileName).also {
            zipTo(it, entries)
        }
}
