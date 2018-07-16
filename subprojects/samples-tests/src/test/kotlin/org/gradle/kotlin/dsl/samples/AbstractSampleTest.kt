package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.junit.Before

import java.io.File


abstract class AbstractSampleTest(val sampleName: String) : AbstractIntegrationTest() {

    @Before
    fun populateProjectRootWithSample() {
        val sampleDir = File(samplesRootDir, sampleName)
        copySampleProject(from = sampleDir, to = projectRoot)
    }
}
