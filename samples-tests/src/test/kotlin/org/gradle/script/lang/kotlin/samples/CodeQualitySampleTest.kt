package org.gradle.script.lang.kotlin.samples

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

import org.junit.Test


class CodeQualitySampleTest : AbstractSampleTest("code-quality") {

    @Test
    fun `code quality plugins are properly configured`() {

        val result = build("build")

        assertThat(result.task(":checkstyleMain").outcome, equalTo(TaskOutcome.SUCCESS))
        assertThat(result.task(":checkstyleTest").outcome, equalTo(TaskOutcome.SUCCESS))

        assertThat(result.task(":findbugsMain").outcome, equalTo(TaskOutcome.SUCCESS))
        assertThat(result.task(":findbugsTest").outcome, equalTo(TaskOutcome.SUCCESS))

        assertThat(result.task(":pmdMain").outcome, equalTo(TaskOutcome.SUCCESS))
        assertThat(result.task(":pmdTest").outcome, equalTo(TaskOutcome.SUCCESS))

        assertThat(result.task(":jdependMain").outcome, equalTo(TaskOutcome.SUCCESS))
        assertThat(result.task(":jdependTest").outcome, equalTo(TaskOutcome.SUCCESS))

        assertThat(result.task(":jacocoTestCoverageVerification").outcome, equalTo(TaskOutcome.SUCCESS))
    }
}
