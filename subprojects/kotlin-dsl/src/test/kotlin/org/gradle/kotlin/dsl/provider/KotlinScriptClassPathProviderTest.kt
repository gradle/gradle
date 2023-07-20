package org.gradle.kotlin.dsl.provider

import org.gradle.kotlin.dsl.support.ProgressMonitor

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Generated

import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_API
import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.accessors.TestWithClassPath
import org.gradle.test.fixtures.file.LeaksFileHandles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


@LeaksFileHandles("embedded Kotlin compiler environment keepalive")
class KotlinScriptClassPathProviderTest : TestWithClassPath() {

    @Test
    fun `should report progress based on the number of entries in gradle-api jar`() {

        val gradleApiJar = jarClassPathWith("gradle-api-3.1.jar", Generated::class).asFiles

        val generatedKotlinExtensions = file("kotlin-dsl-extensions.jar")

        val apiMetadataModule = mockGradleApiMetadataModule()

        val kotlinExtensionsMonitor = mock<ProgressMonitor>(name = "kotlinExtensionsMonitor")
        val progressMonitorProvider = mock<JarGenerationProgressMonitorProvider> {
            on { progressMonitorFor(generatedKotlinExtensions, 3) } doReturn kotlinExtensionsMonitor
        }

        val subject = KotlinScriptClassPathProvider(
            moduleRegistry = mock { on { getExternalModule(any()) } doReturn apiMetadataModule },
            classPathRegistry = mock { on { getClassPath(GRADLE_API.name) } doReturn DefaultClassPath.of(gradleApiJar) },
            coreAndPluginsScope = mock(),
            gradleApiJarsProvider = { gradleApiJar },
            jarCache = { id, generator -> file("$id.jar").apply(generator) },
            progressMonitorProvider = progressMonitorProvider,
            temporaryFileProvider = TestFiles.tmpDirTemporaryFileProvider(tempFolder.createDir("tmp"))
        )

        assertThat(
            subject.gradleKotlinDsl.asFiles.toList(),
            equalTo(gradleApiJar + generatedKotlinExtensions)
        )

        verifyProgressMonitor(kotlinExtensionsMonitor)
    }

    private
    fun verifyProgressMonitor(monitor: ProgressMonitor) {
        verify(monitor, times(3)).onProgress()
        verify(monitor, times(1)).close()
    }

    private
    fun mockGradleApiMetadataModule() =
        withZip(
            "gradle-api-metadata-0.jar",
            sequenceOf(
                "gradle-api-declaration.properties" to "includes=\nexcludes=\n".toByteArray()
            )
        ).let { jar ->
            mock<Module> { on { classpath } doReturn DefaultClassPath.of(listOf(jar)) }
        }
}
