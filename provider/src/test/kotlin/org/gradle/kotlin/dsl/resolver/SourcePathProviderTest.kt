package org.gradle.kotlin.dsl.resolver

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.fixtures.FolderBasedTest
import org.gradle.kotlin.dsl.resolver.SourcePathProvider.sourcePathFor

import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Ignore

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
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = mock()).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")))
    }

    @Ignore("gradle/kotlin-dsl#775")
    @Test
    fun `given multi-project buildSrc folder, it will include buildSrc source roots`() {
        withFolders {
            "project" {
                "buildSrc" {
                    "subprojects/foo/src/main/kotlin" {
                    }
                    "subprojects/bar/src/main/java" {
                    }
                    withFile("settings.gradle.kts", """
                        include("foo")
                        include("bar")
                        for (project in rootProject.children) {
                            project.projectDir = file("subprojects/${'$'}{project.name}")
                        }
                    """)
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
                sourceDistributionResolver = mock()).asFiles,
            hasItems(
                folder("project/buildSrc/subprojects/foo/src/main/kotlin"),
                folder("project/buildSrc/subprojects/bar/src/main/java"),
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
            "sourceDistribution" {
                "src-foo" {}
                "src-bar" {}
            }
        }

        val resolver = mock<SourceDistributionProvider> {
            on { sourceDirs() } doReturn subDirsOf(folder("sourceDistribution"))
        }

        assertThat(
            sourcePathFor(
                classPath = ClassPath.EMPTY,
                projectDir = folder("project"),
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = resolver).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("sourceDistribution/src-foo"),
                folder("sourceDistribution/src-bar")))
    }
}
