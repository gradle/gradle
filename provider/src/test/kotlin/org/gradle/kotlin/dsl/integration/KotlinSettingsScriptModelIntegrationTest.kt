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

    @Test
    fun `can fetch classpath of settings script plugin`() {

        withBuildSrc()

        // TODO: buildscript.dependencies (#180)
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
        //TODO: buildscript.dependencies (#180)
        // assertIncludes(classPath, settingsDependency)
        assertExcludes(classPath, projectDependency)
    }

    @Test
    fun `sourcePath includes buildSrc source roots`() {

        withKotlinBuildSrc()
        val settings = withSettings("""include(":sub")""")

        val sourcePath = sourcePathFor(settings)
        assertSourcePathIncludesBuildSrcProjectDependenciesSources(
            sourcePath,
            mapOf(":" to SourceRoots(listOf(Language.java, Language.kotlin))))
    }

    @Test
    fun `sourcePath includes buildSrc project dependencies source roots`() {

        val sourceRoots = withMultiProjectKotlinBuildSrc()
        val settings = withSettings("""include(":sub")""")

        val sourcePath = sourcePathFor(settings)

        assertSourcePathIncludesBuildSrcProjectDependenciesSources(sourcePath, sourceRoots)
    }
}
