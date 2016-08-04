package org.gradle.script.lang.kotlin.support

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
        File(root, this).apply { mkdirs() }.canonicalFile
}
