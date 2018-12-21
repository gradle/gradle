package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.UncheckedIOException

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.util.GFileUtils

import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement

import kotlin.annotation.AnnotationTarget.FUNCTION


/**
 * A [TemporaryFolder] JUnit rule that fails the test if the temporary folder cannot be deleted
 * when the test finishes and the test is not annotated with [LeaksFileHandles].
 */
open class ForcefullyDeletedTemporaryFolder : TemporaryFolder() {

    override fun delete() = GFileUtils.forceDelete(root)

    override fun apply(base: Statement, description: Description) = object : Statement() {

        override fun evaluate() {
            before()
            try {
                base.evaluate()
            } finally {
                if (allowsDeletion(description)) {
                    if (leaksFileHandles(description)) {
                        ignoringDeleteErrorCausedBy(description) {
                            after()
                        }
                    } else after()
                }
            }
        }
    }

    private
    fun ignoringDeleteErrorCausedBy(description: Description, action: () -> Unit) {
        try {
            action()
        } catch (e: UncheckedIOException) {
            System.err.println(
                "Couldn't delete test dir for `${description.displayName}` (test is holding files open).\n"
                    + "In order to find out which files are held open you may find http://file-leak-detector.kohsuke.org/ useful.")
            e.printStackTrace()
        }
    }

    private
    fun allowsDeletion(description: Description): Boolean =
        description.getAnnotation(DontDeleteTemporaryFolder::class.java) == null

    private
    fun leaksFileHandles(description: Description) =
        description.getAnnotation(LeaksFileHandles::class.java) != null
            || description.testClass.getAnnotation(LeaksFileHandles::class.java) != null
}


/**
 * Declares that the temporary folder used by the test should not be forcefully deleted.
 *
 * Useful during debugging.
 */
@Target(FUNCTION)
annotation class DontDeleteTemporaryFolder(val why: String)
