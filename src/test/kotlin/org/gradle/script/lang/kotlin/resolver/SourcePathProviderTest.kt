package org.gradle.script.lang.kotlin.resolver

import org.gradle.script.lang.kotlin.FolderBasedTest

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

        val request = KotlinBuildScriptModelRequest(
            projectDir = folder("project"),
            gradleInstallation = GradleInstallation.Local(folder("gradle")))

        val emptyModel = StandardKotlinBuildScriptModel(emptyList())
        val sourcePath = SourcePathProvider.sourcePathFor(request, emptyModel)
        assertThat(
            sourcePath,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }
}
