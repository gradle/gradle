package org.gradle.kotlin.dsl.samples

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

import org.junit.Test


class CodeQualitySampleTest : AbstractSampleTest("code-quality") {

    @Test
    fun `code quality plugins are properly configured`() {

        val result = build("build")

        val successfulTasks = listOf(
            ":checkstyleMain",
            ":checkstyleTest",
            ":findbugsMain",
            ":findbugsTest",
            ":pmdMain",
            ":pmdTest",
            ":jdependMain",
            ":jdependTest"
// TODO: investigate why result.outcomeOf("jacocoTestCoverageVerification") returns null below
//            ":jacocoTestCoverageVerification"
        )

        successfulTasks.forEach { taskName ->
            assertThat(
                "$taskName succeeds",
                result.outcomeOf(taskName),
                equalTo(TaskOutcome.SUCCESS)
            )
        }
    }
}
