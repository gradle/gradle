package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.concurrent.future
import org.gradle.kotlin.dsl.embeddedKotlinVersion

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.customDaemonRegistry
import org.gradle.kotlin.dsl.fixtures.customInstallation
import org.gradle.kotlin.dsl.fixtures.withDaemonIdleTimeout
import org.gradle.kotlin.dsl.fixtures.withDaemonRegistry
import org.gradle.kotlin.dsl.fixtures.matching

import org.gradle.kotlin.dsl.resolver.GradleInstallation
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptModelRequest
import org.gradle.kotlin.dsl.resolver.fetchKotlinBuildScriptModelFor
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.util.TextUtil.normaliseFileSeparators

import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matcher
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

        withFile("settings.gradle", """
            include 'foo', 'bar'
        """)

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
            subProjectScript ="""
                buildscript {
                    dependencies { classpath(kotlin("gradle-plugin")) }
                    repositories { jcenter() }
                }
            """)
    }

    @Test
    fun `sourcePath includes buildscript classpath sources resolved against project hierarchy`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = """
                buildscript {
                    dependencies { classpath(kotlin("gradle-plugin")) }
                    repositories { jcenter() }
                }
            """,
            subProjectScript = "")
    }

    @Test
    fun `sourcePath includes plugins classpath sources resolved against project`() {

        assertSourcePathIncludesKotlinPluginSourcesGiven(
            rootProjectScript = "",
            subProjectScript = """ plugins { kotlin("jvm") } """)
    }

    private
    fun assertSourcePathIncludesKotlinStdlibSourcesGiven(rootProjectScript: String, subProjectScript: String) {

        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems("kotlin-stdlib-$embeddedKotlinVersion-sources.jar"))
    }

    private
    fun assertSourcePathIncludesKotlinPluginSourcesGiven(rootProjectScript: String, subProjectScript: String) {

        assertSourcePathGiven(
            rootProjectScript,
            subProjectScript,
            hasItems(
                equalTo("kotlin-gradle-plugin-$embeddedKotlinVersion-sources.jar"),
                matching("commons-io-[0-9.]+-sources\\.jar")))
    }

    private
    fun assertSourcePathGiven(
        rootProjectScript: String,
        subProjectScript: String,
        matches: Matcher<Iterable<String>>) {

        val subProjectName = "sub"
        withFile("settings.gradle", "include '$subProjectName'")

        withBuildScript(rootProjectScript)
        val subProjectScriptFile = withBuildScriptIn(subProjectName, subProjectScript)

        assertThat(sourcePathFor(subProjectScriptFile).map { it.name }, matches)
    }

    private
    fun sourcePathFor(scriptFile: File) =
        kotlinBuildScriptModelFor(projectRoot, scriptFile).sourcePath

    private
    fun assertContainsGradleKotlinDslJars(classPath: List<File>) {
        val version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.map { it.name },
            hasItems(
                matching("gradle-kotlin-dsl-$version\\.jar"),
                matching("gradle-api-$version\\.jar"),
                matching("gradle-kotlin-dsl-extensions-$version\\.jar")))
    }

    private
    fun assertClassPathFor(buildScript: File, includes: Set<File>, excludes: Set<File>) =
        assertThat(
            classPathFor(projectRoot, buildScript).map { it.name },
            allOf(
                hasItems(*includes.map { it.name }.toTypedArray()),
                not(hasItems(*excludes.map { it.name }.toTypedArray()))))

    private
    fun assertClassPathContains(vararg files: File) {
        val fileNameSet = files.map { it.name }.toSet().toTypedArray()
        assert(fileNameSet.size == files.size)
        assertThat(
            canonicalClassPath().map { it.name },
            hasItems(*fileNameSet))
    }

    private
    fun assertContainsBuildSrc(classPath: List<File>) {
        assertThat(
            classPath.map { it.name },
            hasItem("buildSrc.jar"))
    }

    private
    fun canonicalClassPath() =
        canonicalClassPathFor(projectRoot)
}


internal
fun canonicalClassPathFor(projectDir: File, scriptFile: File? = null) =
    classPathFor(projectDir, scriptFile).map(File::getCanonicalFile)


private
fun classPathFor(projectDir: File, scriptFile: File?) =
    kotlinBuildScriptModelFor(projectDir, scriptFile).classPath


internal
fun kotlinBuildScriptModelFor(projectDir: File, scriptFile: File? = null): KotlinBuildScriptModel =
    withDaemonRegistry(customDaemonRegistry()) {
        withDaemonIdleTimeout(1) {
            future {
                fetchKotlinBuildScriptModelFor(
                    KotlinBuildScriptModelRequest(
                        projectDir = projectDir,
                        scriptFile = scriptFile,
                        gradleInstallation = GradleInstallation.Local(customInstallation()),
                        jvmOptions = listOf("-Xms128m", "-Xmx128m"))) {

                    setStandardOutput(System.out)
                    setStandardError(System.err)
                }
            }.get()
        }
    }
