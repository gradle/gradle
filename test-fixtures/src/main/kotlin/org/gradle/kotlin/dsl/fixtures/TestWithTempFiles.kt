package org.gradle.kotlin.dsl.fixtures

import org.junit.Rule

import java.io.File

abstract class TestWithTempFiles {

    @JvmField
    @Rule val tempFolder = ForcefullyDeletedTemporaryFolder()

    protected
    val root: File
        get() = tempFolder.root

    protected
    fun file(fileName: String) =
        File(root, fileName)

    protected
    fun newFile(fileName: String) =
        tempFolder.newFile(fileName)!!

    protected
    fun newFile(fileName: String, text: String): File =
        newFile(fileName).apply { writeText(text) }
}
