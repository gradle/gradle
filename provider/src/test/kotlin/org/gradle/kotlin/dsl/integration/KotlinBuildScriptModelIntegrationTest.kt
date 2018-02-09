package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.util.TextUtil.normaliseFileSeparators

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not

import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class KotlinBuildScriptModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `can fetch buildSrc classpath in face of compilation errors`() {

        withBuildSrc()

        withBuildScript("""
            val p =
        """)

        assertContainsBuildSrc(canonicalClassPath())
    }

    @Test
    fun `can fetch buildscript classpath in face of compilation errors`() {

        withFile("classes.jar")

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }

            val p =
        """)

        assertClassPathContains(
            existing("classes.jar"))
    }

    @Test
    fun `can fetch buildscript classpath of top level Groovy script`() {

        withBuildSrc()

        withFile("classes.jar", "")

        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }
        """)

        val classPath = canonicalClassPath()
        assertThat(
            classPath.map { it.name },
            hasItem("classes.jar"))

        assertContainsBuildSrc(classPath)

        assertContainsGradleKotlinDslJars(classPath)
    }

    @Test
    fun `can fetch buildscript classpath for sub-project script`() {

        withSettings("include(\"foo\", \"bar\")")

        fun withFixture(fixture: String) =
            withClassJar("libs/$fixture.jar", DeepThought::class.java)

        val parentJar = withFixture("parent")
        val fooJar    = withFixture("foo")
        val barJar    = withFixture("bar")

        fun String.withBuildscriptDependencyOn(fixture: File) =
            withFile(this, """
                buildscript {
                    dependencies { classpath(files("${normaliseFileSeparators(fixture.path)}")) }
                }
            """)

        val parentBuildScript = "build.gradle".withBuildscriptDependencyOn(parentJar)
        val fooBuildScript    = "foo/build.gradle.kts".withBuildscriptDependencyOn(fooJar)
        val barBuildScript    = "bar/build.gradle.kts".withBuildscriptDependencyOn(barJar)

        assertClassPathFor(
            parentBuildScript,
            includes = setOf(parentJar),
            excludes = setOf(fooJar, barJar))

        assertClassPathFor(
            fooBuildScript,
            includes = setOf(parentJar, fooJar),
            excludes = setOf(barJar))

        assertClassPathFor(
            barBuildScript,
            includes = setOf(parentJar, barJar),
            excludes = setOf(fooJar))
    }

    @Test
    fun `can fetch classpath of script plugin`() {

        withBuildSrc()

        withFile("classes.jar")

        withFile("build.gradle", """
            buildscript {
                dependencies { classpath(files("classes.jar")) }
            }
        """)

        val scriptPlugin = withFile("plugin.gradle.kts")

        val classPath = canonicalClassPathFor(projectRoot, scriptPlugin)
        assertThat(
            classPath,
            not(hasItem(existing("classes.jar"))))

        assertContainsBuildSrc(classPath)

        assertContainsGradleKotlinDslJars(classPath)
    }

    @Test
    fun `can fetch classpath of plugin portal plugin in plugins block`() {
        withBuildScript("""
            plugins {
                id("org.gradle.hello-world") version "0.2"
            }
        """)

        assertThat(
            canonicalClassPath().map { it.name },
            hasItems("gradle-hello-world-plugin-0.2.jar"))
    }

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
            subProjectScript = "buildscript { repositories { jcenter() } }")
    }

    @Test
    fun `sourcePath includes kotlin-stdlib sources resolved against project hierarchy`() {

        assertSourcePathIncludesKotlinStdlibSourcesGiven(
            rootProjectScript = "buildscript { repositories { jcenter() } }",
            subProjectScript = "")
    }

    @Test
    fun `sourcePath includes buildscript classpath sources resolved against project`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = "",
            subProjectScript = """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    repositories { jcenter() }
                }
            """)
    }

    @Test
    fun `sourcePath includes buildscript classpath sources resolved against project hierarchy`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = """
                buildscript {
                    dependencies { classpath(embeddedKotlin("gradle-plugin")) }
                    repositories { jcenter() }
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
}
