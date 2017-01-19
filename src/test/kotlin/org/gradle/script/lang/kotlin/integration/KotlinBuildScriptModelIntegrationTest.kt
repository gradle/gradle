package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.integration.fixture.DeepThought

import org.gradle.script.lang.kotlin.matching

import org.gradle.script.lang.kotlin.support.KotlinBuildScriptModel
import org.gradle.script.lang.kotlin.support.KotlinBuildScriptModelRequest
import org.gradle.script.lang.kotlin.support.fetchKotlinBuildScriptModelFor

import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

class KotlinBuildScriptModelIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can fetch buildSrc classpath in face of compilation errors`() {

        withBuildSrc()

        withBuildScript("""
            val p =
        """)

        assertClassPathContains(
            buildSrcOutputFolder())
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
            classPath,
            hasItems(
                buildSrcOutputFolder(),
                existing("classes.jar")))

        assertContainsGradleScriptKotlinApiJars(classPath)
    }

    @Test
    fun `can fetch buildscript classpath for sub-project script`() {

        withFile("settings.gradle", """
            include 'foo', 'bar'
            rootProject.children.each {
                it.buildFileName = 'build.gradle.kts'
            }
        """)

        withClassJar("libs/fixture-foo.jar", DeepThought::class.java)

        withClassJar("libs/fixture-bar.jar", DeepThought::class.java)

        withFile("foo/build.gradle.kts", """
            buildscript {
                dependencies { classpath(files("../libs/fixture-foo.jar")) }
            }
        """)

        withFile("bar/build.gradle.kts", """
            buildscript {
                dependencies { classpath(files("../libs/fixture-bar.jar")) }
            }
        """)

        assertThat(
            canonicalClassPathFor(projectRoot, existing("foo/build.gradle.kts")),
            allOf(
                hasItem(existing("libs/fixture-foo.jar")),
                not(hasItem(existing("libs/fixture-bar.jar")))))

        assertThat(
            canonicalClassPathFor(projectRoot, existing("bar/build.gradle.kts")),
            allOf(
                hasItem(existing("libs/fixture-bar.jar")),
                not(hasItem(existing("libs/fixture-foo.jar")))))
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
            allOf(
                hasItem(buildSrcOutputFolder()),
                not(hasItem(existing("classes.jar")))))

        assertContainsGradleScriptKotlinApiJars(classPath)
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

    private fun assertContainsGradleScriptKotlinApiJars(classPath: List<File>) {
        val version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.map { it.name },
            hasItems(
                matching("gradle-script-kotlin-$version\\.jar"),
                matching("gradle-api-$version\\.jar"),
                matching("gradle-script-kotlin-extensions-$version\\.jar")))
    }

    private fun assertClassPathContains(vararg files: File) {
        assertThat(
            canonicalClassPath(),
            hasItems(*files))
    }

    private fun buildSrcOutputFolder(): File =
        existing("buildSrc/build/classes/main")

    private fun canonicalClassPath() =
        canonicalClassPathFor(projectRoot)

    private fun canonicalClassPathFor(projectDir: File, scriptFile: File? = null) =
        kotlinBuildScriptModelFor(projectDir, scriptFile).classPath.map(File::getCanonicalFile)

    private fun kotlinBuildScriptModelFor(projectDir: File, scriptFile: File? = null): KotlinBuildScriptModel =
        withDaemonRegistry(customDaemonRegistry()) {
            fetchKotlinBuildScriptModelFor(
                KotlinBuildScriptModelRequest(
                    projectDir = projectDir,
                    scriptFile = scriptFile,
                    gradleInstallation = customInstallation()))!!
        }

    private fun customDaemonRegistry() =
        File("build/custom/daemon-registry")
}
