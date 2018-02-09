package org.gradle.kotlin.dsl.integration

import org.gradle.util.TextUtil

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
}
