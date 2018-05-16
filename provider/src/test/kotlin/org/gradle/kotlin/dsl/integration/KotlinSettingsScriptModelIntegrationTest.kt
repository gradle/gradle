package org.gradle.kotlin.dsl.integration

import org.gradle.util.TextUtil
import org.junit.Assert.assertThat

import org.junit.Test


class KotlinSettingsScriptModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `can fetch classpath of settings script`() {

        withBuildSrc()

        val settingsDependency = withFile("settings-dependency.jar", "")
        val settings = withSettings("""
            buildscript {
                dependencies {
                    classpath(files("${TextUtil.normaliseFileSeparators(settingsDependency.path)}"))
                }
            }
        """)

        val projectDependency = withFile("project-dependency.jar", "")
        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("${TextUtil.normaliseFileSeparators(projectDependency.path)}"))
                }
            }
        """)

        val classPath = canonicalClassPathFor(projectRoot, settings)

        assertContainsBuildSrc(classPath)
        assertContainsGradleKotlinDslJars(classPath)
        assertIncludes(classPath, settingsDependency)
        assertExcludes(classPath, projectDependency)
    }

    @Test
    fun `can fetch classpath of settings script plugin`() {

        withBuildSrc()

        val settingsDependency = withFile("settings-dependency.jar", "")
        val settings = withFile("my.settings.gradle.kts", """
            buildscript {
                dependencies {
                    classpath(files("${TextUtil.normaliseFileSeparators(settingsDependency.path)}"))
                }
            }
        """)

        val projectDependency = withFile("project-dependency.jar", "")
        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("${TextUtil.normaliseFileSeparators(projectDependency.path)}"))
                }
            }
        """)

        val classPath = canonicalClassPathFor(projectRoot, settings)

        assertContainsBuildSrc(classPath)
        assertContainsGradleKotlinDslJars(classPath)
        assertIncludes(classPath, settingsDependency)
        assertExcludes(classPath, projectDependency)
    }

    @Test
    fun `sourcePath includes buildSrc source roots`() {

        withKotlinBuildSrc()
        val settings = withSettings("""include(":sub")""")

        assertThat(
            sourcePathFor(settings),
            matchesProjectsSourceRoots(withMainSourceSetJavaKotlinIn("buildSrc")))
    }

    @Test
    fun `sourcePath includes buildSrc project dependencies source roots`() {

        val sourceRoots = withMultiProjectKotlinBuildSrc()
        val settings = withSettings("""include(":sub")""")

        assertThat(
            sourcePathFor(settings),
            matchesProjectsSourceRoots(*sourceRoots))
    }
}
