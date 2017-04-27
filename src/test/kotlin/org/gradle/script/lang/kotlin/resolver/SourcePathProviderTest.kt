package org.gradle.script.lang.kotlin.resolver

import org.gradle.script.lang.kotlin.FolderBasedTest
import org.gradle.script.lang.kotlin.resolver.SourcePathProvider.sourcePathFor

import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

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
                classPath = emptyList(),
                projectDir = folder("project"),
                gradleHome = folder("gradle")),
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }
}
