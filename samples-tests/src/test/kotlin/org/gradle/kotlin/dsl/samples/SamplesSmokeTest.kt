package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not

import org.junit.Assume.assumeThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File


@RunWith(Parameterized::class)
class SamplesSmokeTest(val sampleDir: File) : AbstractIntegrationTest() {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic fun testCases(): Iterable<File> =
            samplesRootDir.listFiles().filter { it.isDirectory }
    }

    @Before
    fun populateProjectRootWithSample() {
        assumeThat(sampleDir.name, not(containsString("android")))
        copySampleProject(from = sampleDir, to = projectRoot)
    }

    @Test
    fun `tasks task succeeds on `() {
        build("tasks")
    }
}
