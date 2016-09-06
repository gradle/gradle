package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.support.ProgressMonitor
import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.zipTo

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever

import org.gradle.script.lang.kotlin.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class KotlinScriptClassPathProviderTest : TestWithTempFiles() {

    @Test
    fun `should report progress based on the number of entries in gradle-api jar`() {

        val gradleApiJar = file("gradle-api-3.1.jar").apply {
            val entries = classEntriesFor(
                org.gradle.api.Action::class.java,
                org.gradle.api.specs.Spec::class.java)
            zipTo(this, entries)
        }

        val generatedKotlinApi = file("script-kotlin-api.jar")
        val generatedKotlinExtensions = file("script-kotlin-extensions.jar")

        val progressMonitorProvider = mock<JarGenerationProgressMonitorProvider>()

        val kotlinApiMonitor = mock<ProgressMonitor>("kotlinApiMonitor")
        whenever(progressMonitorProvider.progressMonitorFor(generatedKotlinApi, 2))
            .thenReturn(kotlinApiMonitor)

        val kotlinExtensionsMonitor = mock<ProgressMonitor>("kotlinExtensionsMonitor")
        whenever(progressMonitorProvider.progressMonitorFor(generatedKotlinExtensions, 2))
            .thenReturn(kotlinExtensionsMonitor)

        val subject = KotlinScriptClassPathProvider(
            classPathRegistry = mock(),
            gradleApiJarsProvider = { listOf(gradleApiJar) },
            jarCache = { id, generator -> file("$id.jar").apply(generator) },
            progressMonitorProvider = progressMonitorProvider)

        assertThat(
            subject.gradleApi.asFiles,
            equalTo(listOf(generatedKotlinApi, generatedKotlinExtensions)))

        verifyProgressMonitor(kotlinApiMonitor)
        verifyProgressMonitor(kotlinExtensionsMonitor)
    }

    private fun verifyProgressMonitor(monitor: ProgressMonitor) {
        verify(monitor, times(2)).onProgress()
        verify(monitor, times(1)).close()
    }
}
