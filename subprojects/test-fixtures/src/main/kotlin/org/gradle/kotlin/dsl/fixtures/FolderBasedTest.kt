package org.gradle.kotlin.dsl.fixtures

import org.junit.Rule

import java.io.File


abstract class FolderBasedTest {

    @JvmField
    @Rule val tempFolder = ForcefullyDeletedTemporaryFolder()

    fun withFolders(folders: FoldersDslExpression) =
        tempFolder.root.withFolders(folders)

    fun folder(path: String) =
        existing(path).apply {
            assert(isDirectory)
        }

    fun file(path: String) =
        existing(path).apply {
            assert(isFile)
        }

    private
    fun existing(path: String): File =
        File(tempFolder.root, path).canonicalFile.apply {
            assert(exists())
        }
}


typealias FoldersDslExpression = FoldersDsl.() -> Unit


fun File.withFolders(folders: FoldersDslExpression) =
    apply { FoldersDsl(this).folders() }


class FoldersDsl(val folder: File) {

    operator fun String.invoke(subFolders: FoldersDslExpression): File =
        (+this).withFolders(subFolders)

    operator fun String.unaryPlus(): File =
        canonicalFile(this).apply { mkdirs() }

    fun withFile(fileName: String, content: String = "") =
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
        File(folder, fileName)
}
