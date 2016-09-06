package org.gradle.script.lang.kotlin

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.io.File

abstract class TestWithTempFiles {

    @JvmField
    @Rule val tempDir = TemporaryFolder()

    protected val root: File
        get() = tempDir.root

    protected fun file(fileName: String) =
        File(root, fileName)

    protected fun newFile(fileName: String) =
        tempDir.newFile(fileName)!!
}
