package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.matching

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class KotlinBuildScriptModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `sourcePath includes Gradle sources`() {

        assertSourcePathIncludesGradleSourcesGiven(
            rootProjectScript = "",
            subProjectScript = "")
    }

    @Test
    fun `sourcePath includes kotlin-stdlib sources resolved against project`() {

        assertSourcePathIncludesKotlinStdlibSourcesGiven(
            rootProjectScript = "",
            subProjectScript = "buildscript { $repositoriesBlock }")
    }

    @Test
    fun `sourcePath includes kotlin-stdlib sources resolved against project hierarchy`() {

        assertSourcePathIncludesKotlinStdlibSourcesGiven(
            rootProjectScript = "buildscript { $repositoriesBlock }",
            subProjectScript = "")
    }

    @Test
    fun `sourcePath includes buildscript classpath sources resolved against project`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = "",
            subProjectScript = """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    $repositoriesBlock
                }
            """)
    }

    @Test
    fun `sourcePath includes buildscript classpath sources resolved against project hierarchy`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    $repositoriesBlock
                }
            """,
            subProjectScript = "")
    }

    @Test
    fun `sourcePath includes plugins classpath sources resolved against project`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = "",
            subProjectScript = """ plugins { kotlin("jvm") version "$embeddedKotlinVersion" } """)
    }

    @Test
    fun `sourcePath includes buildSrc source roots`() {

        withKotlinBuildSrc()
        withSettings("""include(":sub")""")

        assertThat(
            sourcePathFor(withFile("sub/build.gradle.kts")),
            matchesProjectsSourceRoots(withMainSourceSetJavaKotlinIn("buildSrc")))
    }

    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    @Test
    fun `sourcePath includes buildSrc project dependencies source roots`() {

        val sourceRoots = withMultiProjectKotlinBuildSrc()
        withSettings("""include(":sub")""")

        assertThat(
            sourcePathFor(withFile("sub/build.gradle.kts")),
            matchesProjectsSourceRoots(*sourceRoots))
    }

    private
    fun assertSourcePathIncludesGradleSourcesGiven(rootProjectScript: String, subProjectScript: String) {

        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems("core-api"))
    }

    private
    fun assertSourcePathIncludesKotlinStdlibSourcesGiven(rootProjectScript: String, subProjectScript: String) {

        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems("kotlin-stdlib-jdk8-$embeddedKotlinVersion-sources.jar"))
    }

    private
    fun assertSourcePathIncludesKotlinPluginSourcesGiven(rootProjectScript: String, subProjectScript: String) {

        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems(
                equalTo("kotlin-gradle-plugin-$embeddedKotlinVersion-sources.jar"),
                matching("annotations-[0-9.]+-sources\\.jar")))
    }

    private
    fun assertSourcePathGiven(
        rootProjectScript: String,
        subProjectScript: String,
        matches: Matcher<Iterable<String>>
    ) {

        val subProjectName = "sub"
        withSettings("""
            include("$subProjectName")
        """)

        withBuildScript(rootProjectScript)
        val subProjectScriptFile = withBuildScriptIn(subProjectName, subProjectScript)

        val srcConventionalPathDirNames = listOf("java", "groovy", "kotlin", "resources")
        val sourcePath = sourcePathFor(subProjectScriptFile).map { path ->
            when {
                srcConventionalPathDirNames.contains(path.name) -> path.parentFile.parentFile.parentFile.name
                else -> path.name
            }
        }.distinct()
        assertThat(sourcePath, matches)
    }
}
