package org.gradle.kotlin.dsl.resolver

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.fixtures.FolderBasedTest
import org.gradle.kotlin.dsl.resolver.SourcePathProvider.sourcePathFor
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.Test


class SourcePathProviderTest : FolderBasedTest() {

    /**
     * This unit test can't rely on `BuildSrcClassPathModeConfigurationAction`
     * it is testing the fallback behavior of [SourcePathProvider]
     */
    @Test
    fun `given buildSrc folder, it will fallback to approximate buildSrc source roots`() {
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
                scriptFile = null,
                projectDir = folder("project"),
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = mock()
            ).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")
            )
        )
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
                scriptFile = null,
                projectDir = folder("project"),
                gradleHomeDir = folder("gradle"),
                sourceDistributionResolver = resolver
            ).asFiles,
            hasItems(
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar"),
                folder("sourceDistribution/src-foo"),
                folder("sourceDistribution/src-bar")
            )
        )
    }

    @Test
    fun `when setting is parsed, buildSrc sources are not available`() {
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

        val sourcePath = sourcePathFor(
            classPath = ClassPath.EMPTY,
            scriptFile = root.resolve("settings.gradle.kts"),
            projectDir = folder("project"),
            gradleHomeDir = folder("gradle"),
            sourceDistributionResolver = mock()
        ).asFiles

        assertThat(
            sourcePath,
            hasItems(
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar")
            )
        )
        assertThat(
            sourcePath,
            not(
                anyOf(
                    hasItem(folder("project/buildSrc/src/main/foo")),
                    hasItem(folder("project/buildSrc/src/main/bar"))
                )
            )
        )
    }

    @Test
    fun `when build file is parsed, buildSrc sources are available`() {
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

        val sourcePath = sourcePathFor(
            classPath = ClassPath.EMPTY,
            scriptFile = root.resolve("build.gradle.kts"),
            projectDir = folder("project"),
            gradleHomeDir = folder("gradle"),
            sourceDistributionResolver = mock()
        ).asFiles

        assertThat(
            sourcePath,
            hasItems(
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar"),
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar")
            )
        )
    }

    @Test
    fun `when project scripts are parsed, buildSrc sources are available`() {
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

        val sourcePath = sourcePathFor(
            classPath = ClassPath.EMPTY,
            scriptFile = root.resolve("plugin.gradle.kts"),
            projectDir = folder("project"),
            gradleHomeDir = folder("gradle"),
            sourceDistributionResolver = mock()
        ).asFiles

        assertThat(
            sourcePath,
            hasItems(
                folder("gradle/src/gradle-foo"),
                folder("gradle/src/gradle-bar"),
                folder("project/buildSrc/src/main/foo"),
                folder("project/buildSrc/src/main/bar")
            )
        )
    }
}
