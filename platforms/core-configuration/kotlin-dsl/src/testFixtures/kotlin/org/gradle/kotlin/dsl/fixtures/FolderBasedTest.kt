package org.gradle.kotlin.dsl.fixtures

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider

import org.junit.Rule

import java.io.File


@CleanupTestDirectory(fieldName = "tempFolder")
abstract class FolderBasedTest {

    @JvmField
    @Rule
    val tempFolder = TestNameTestDirectoryProvider(javaClass)

    val root: File
        get() = tempFolder.testDirectory

    fun withFolders(folders: FoldersDslExpression) =
        root.withFolders(folders)

    fun folder(path: String): File =
        existing(path).apply {
            assert(isDirectory)
        }

    fun file(path: String): File =
        existing(path).apply {
            assert(isFile)
        }

    private
    fun existing(path: String): File =
        File(root, path).canonicalFile.apply {
            assert(exists())
        }
}


typealias FoldersDslExpression = FoldersDsl.() -> Unit


fun File.withFolders(folders: FoldersDslExpression) =
    apply { FoldersDsl(this).folders() }


class FoldersDsl(val root: File) {

    operator fun String.invoke(subFolders: FoldersDslExpression): File =
        (+this).withFolders(subFolders)

    operator fun String.unaryPlus(): File =
        canonicalFile(this).apply { mkdirs() }

    fun withFile(fileName: String, content: String = ""): File =
        canonicalFile(fileName).apply {
            parentFile.mkdirs()
            writeText(content)
        }

    fun existing(fileName: String): File =
        canonicalFile(fileName).also {
            require(it.exists()) { "$it doesn't exist" }
        }

    fun canonicalFile(fileName: String): File =
        file(fileName).canonicalFile

    fun file(fileName: String): File =
        root.resolve(fileName)
}
