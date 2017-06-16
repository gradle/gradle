package org.gradle.kotlin.dsl.resolver

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.resolver.SourcePathProvider.sourcePathFor

import org.gradle.kotlin.dsl.fixtures.FolderBasedTest

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
                classPath = ClassPath.EMPTY,
                projectDir = folder("project"),
                gradleHome = folder("gradle")).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }
}
