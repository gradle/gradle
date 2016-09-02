package org.gradle.script.lang.kotlin

import org.junit.Rule
import org.junit.rules.TemporaryFolder

abstract class TestWithTempFiles {

    @JvmField
    @Rule val tempDir = TemporaryFolder()

    protected fun tempFile(fileName: String) =
        tempDir.newFile(fileName)!!
}
