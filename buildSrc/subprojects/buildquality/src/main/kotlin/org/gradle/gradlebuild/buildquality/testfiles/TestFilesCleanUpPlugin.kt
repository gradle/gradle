package org.gradle.gradlebuild.buildquality.testfiles

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.gradlebuild.testing.integrationtests.cleanup.EmptyDirectoryCheck
import org.gradle.kotlin.dsl.*


/**
 * Generate a report showing which tests in a subproject are leaving
 * files around.
 *
 * Once a subproject's report is "clean" we should remove the following from that
 * subproject's buildscript:
 *
 * verifyTestFilesCleanup.errorWhenNotEmpty = false
 */
open class TestFilesCleanUpPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val verifyTestFilesCleanup by tasks.creating(EmptyDirectoryCheck::class) {
            targetDir = fileTree("$buildDir/tmp/test files")
            report = file("$buildDir/reports/remains.txt")
            isErrorWhenNotEmpty = true
        }
        extensions.create<TestFileCleanUpExtension>("testFilesCleanup", verifyTestFilesCleanup)
    }
}


open class TestFileCleanUpExtension(val verifyTestFilesCleanup: EmptyDirectoryCheck) {
    var isErrorWhenNotEmpty: Boolean
        get() = verifyTestFilesCleanup.isErrorWhenNotEmpty
        set(value) {
            verifyTestFilesCleanup.isErrorWhenNotEmpty = value
        }
}
