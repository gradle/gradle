package org.gradle.script.lang.kotlin

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.io.File

abstract class FolderBasedTest {

    @JvmField
    @Rule val tempFolder = TemporaryFolder()

    fun withFolders(folders: FoldersDslExpression) =
        tempFolder.root.withFolders(folders)

    fun folder(path: String): File =
        File(tempFolder.root, path).canonicalFile
}

typealias FoldersDslExpression = FoldersDsl.() -> Unit

fun File.withFolders(folders: FoldersDslExpression) =
    apply { FoldersDsl(this).folders() }

class FoldersDsl(val root: File) {

    operator fun String.invoke(subFolders: FoldersDslExpression): File =
        (+this).withFolders(subFolders)

    operator fun String.unaryPlus(): File =
        asCanonicalFile().apply { mkdirs() }

    fun withFile(fileName: String, content: String) =
        fileName.asCanonicalFile().apply { parentFile.mkdirs() }.writeText(content)

    private fun String.asCanonicalFile(): File =
        File(root, this).canonicalFile

}
