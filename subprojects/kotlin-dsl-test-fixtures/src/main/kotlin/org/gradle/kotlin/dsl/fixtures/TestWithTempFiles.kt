package org.gradle.kotlin.dsl.fixtures

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

import org.gradle.kotlin.dsl.support.zipTo

import org.junit.Rule

import java.io.File


@CleanupTestDirectory(fieldName = "tempFolder")
abstract class TestWithTempFiles {

    @JvmField
    @Rule
    val tempFolder = TestNameTestDirectoryProvider()

    protected
    val root: TestFile
        get() = tempFolder.testDirectory

    /**
     * See [org.junit.rules.TemporaryFolder.newFolder]
     */
    fun newFolder(): TestFile =
        root.createDir()

    protected
    fun file(fileName: String): TestFile =
        root.file(fileName)

    protected
    fun newFile(fileName: String): TestFile =
        root.createFile(fileName)

    protected
    fun newFile(fileName: String, text: String): TestFile =
        newFile(fileName).apply { writeText(text) }

    protected
    fun newFolder(vararg folderNames: String): TestFile =
        root.createDir(folderNames.joinToString(File.separator))

    protected
    fun withZip(fileName: String, entries: Sequence<Pair<String, ByteArray>>): TestFile =
        newFile(fileName).also {
            zipTo(it, entries)
        }
}
