package org.gradle.script.lang.kotlin.support

import org.gradle.script.lang.kotlin.FolderBasedTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.hasItems

import org.junit.Test

class DefaultSourcePathProviderTest : FolderBasedTest() {

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
            gradleInstallation =  folder("gradle"))

        val emptyModel = StandardKotlinBuildScriptModel(emptyList())
        val sourcePath = DefaultSourcePathProvider.sourcePathFor(request, emptyModel)
        assertThat(
            sourcePath,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }
}
