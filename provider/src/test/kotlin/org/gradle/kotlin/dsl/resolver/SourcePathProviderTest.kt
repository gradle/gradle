package org.gradle.kotlin.dsl.resolver

import com.nhaarman.mockito_kotlin.whenever
import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.fixtures.FolderBasedTest
import org.gradle.kotlin.dsl.resolver.SourcePathProvider.sourcePathFor
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

class SourcePathProviderTest : FolderBasedTest() {

    @Test
    fun `given buildSrc folder, it will include buildSrc source roots`() {
        withFolders {
            "project" {
                "buildSrc/src/main" {
                    +"foo"
                    +"bar"
                }
            }
            "gradle" {
                "src" {
                    +"gradle-foo"
                    +"gradle-bar"
                }
            }
        }

        assertThat(
            sourcePathFor(
                classPath = ClassPath.EMPTY,
                projectDir = folder("project"),
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = mock(SourceDistributionProvider::class.java)).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }

    @Test
    fun `when src dir is missing from Gradle distribution, it will try to download it`() {
        withFolders {
            "project" {
                "buildSrc/src/main" {
                    +"foo"
                    +"bar"
                }
            }
            "gradle" {

            }
        }

        val resolver = mock(SourceDistributionProvider::class.java)

        whenever(resolver.downloadAndResolveSources())
            .thenReturn(listOf(tempFolder.newFolder("gradle", "src", "gradle-foo")))

        assertThat(
            sourcePathFor(
                classPath = ClassPath.EMPTY,
                projectDir = folder("project"),
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = resolver).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo")))
    }
}
