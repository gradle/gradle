package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.FoldersDsl
import org.gradle.kotlin.dsl.fixtures.matching
import org.gradle.kotlin.dsl.fixtures.normalisedPath

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matcher

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
    fun `can fetch buildSrc classpath in face of buildscript exceptions`() {

        withBuildSrc()

        withBuildScript("""
            buildscript { TODO() }
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
    fun `can fetch classpath in face of buildSrc test failures`() {
        withKotlinBuildSrc()
        existing("buildSrc/build.gradle.kts").let { buildSrcScript ->
            buildSrcScript.writeText(buildSrcScript.readText() + """
                dependencies {
                    testImplementation("junit:junit:4.12")
                }
            """)
        }
        withFile("buildSrc/src/test/kotlin/FailingTest.kt", """
            class FailingTest {
                @org.junit.Test fun test() {
                    throw Exception("BOOM")
                }
            }
        """)

        assertContainsBuildSrc(canonicalClassPath())
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
    fun `can fetch buildscript classpath for sub-project script when parent has errors`() {

        withSettings("""include("sub")""")

        withBuildScript("val p =")

        val jar = withClassJar("libs/jar.jar")

        val subProjectScript =
            withFile("sub/build.gradle.kts", """
                buildscript {
                    dependencies { classpath(files("${jar.normalisedPath}")) }
                }
            """)

        assertClassPathFor(
            subProjectScript,
            includes = setOf(jar),
            excludes = setOf()
        )
    }

    @Test
    fun `can fetch buildscript classpath for sub-project script`() {

        withSettings("include(\"foo\", \"bar\")")

        fun withFixture(fixture: String) =
            withClassJar("libs/$fixture.jar", DeepThought::class.java)

        val parentJar = withFixture("parent")
        val fooJar = withFixture("foo")
        val barJar = withFixture("bar")

        val parentBuildScript = "build.gradle".withBuildscriptDependencyOn(parentJar)
        val fooBuildScript = "foo/build.gradle.kts".withBuildscriptDependencyOn(fooJar)
        val barBuildScript = "bar/build.gradle.kts".withBuildscriptDependencyOn(barJar)

        assertClassPathFor(
            parentBuildScript,
            includes = setOf(parentJar),
            excludes = setOf(fooJar, barJar)
        )

        assertClassPathFor(
            fooBuildScript,
            includes = setOf(parentJar, fooJar),
            excludes = setOf(barJar)
        )

        assertClassPathFor(
            barBuildScript,
            includes = setOf(parentJar, barJar),
            excludes = setOf(fooJar)
        )
    }

    @Test
    fun `can fetch buildscript classpath for buildSrc sub-project script`() {

        withDefaultSettings()

        withFolders {
            "libs" {
                withJar("root-dep.jar")
                withJar("buildSrc-root-dep.jar")
                withJar("buildSrc-sub-dep.jar")
            }

            "buildSrc" {
                withFile("settings.gradle.kts", """
                    include("sub")
                    project(":sub").apply {
                        projectDir = file("../buildSrc-sub")
                        buildFileName = "sub.gradle.kts"
                    }
                    $defaultSettingsScript
                """)
            }

            "buildSrc-sub" {
            }
        }

        val rootDependency = existing("libs/root-dep.jar")
        val buildSrcDependency = existing("libs/buildSrc-root-dep.jar")
        val buildSrcSubDependency = existing("libs/buildSrc-sub-dep.jar")

        val rootBuildScript = "build.gradle".withBuildscriptDependencyOn(rootDependency)
        val buildSrcBuildScript = "buildSrc/build.gradle.kts".withBuildscriptDependencyOn(buildSrcDependency)
        val buildSrcSubBuildScript = "buildSrc-sub/sub.gradle.kts".withBuildscriptDependencyOn(buildSrcSubDependency)

        assertClassPathFor(
            rootBuildScript,
            includes = setOf(rootDependency),
            excludes = setOf(buildSrcDependency, buildSrcSubDependency)
        )

        assertClassPathFor(
            buildSrcBuildScript,
            includes = setOf(buildSrcDependency),
            excludes = setOf(rootDependency, buildSrcSubDependency)
        )

        assertClassPathFor(
            buildSrcSubBuildScript,
            includes = setOf(buildSrcDependency, buildSrcSubDependency),
            excludes = setOf(rootDependency)
        )
    }

    @Test
    fun `can fetch buildscript classpath for sub-project script outside root project dir`() {

        withFolders {

            "libs" {
                withJar("root.jar")
                withJar("sub.jar")
            }

            "root" {
                withFile("settings.gradle.kts", """
                    include("sub")
                    project(":sub").apply {
                        projectDir = file("../sub")
                        buildFileName = "sub.gradle.kts"
                    }
                """)
            }

            "sub" {
            }
        }

        val rootDependency = existing("libs/root.jar")
        val subDependency = existing("libs/sub.jar")

        val rootBuildScript = "root/build.gradle".withBuildscriptDependencyOn(rootDependency)
        val subBuildScript = "sub/sub.gradle.kts".withBuildscriptDependencyOn(subDependency)
        val rootProjectDir = rootBuildScript.parentFile

        assertClassPathFor(
            rootBuildScript,
            includes = setOf(rootDependency),
            excludes = setOf(subDependency),
            projectDir = rootProjectDir
        )

        assertClassPathFor(
            subBuildScript,
            includes = setOf(rootDependency, subDependency),
            excludes = emptySet(),
            projectDir = rootProjectDir
        )
    }

    private
    fun FoldersDsl.withJar(named: String): File =
        withJar(named.asCanonicalFile())

    private
    fun withJar(asCanonicalFile: File): File =
        withClassJar(asCanonicalFile.path, DeepThought::class.java)

    private
    fun String.withBuildscriptDependencyOn(file: File) =
        withFile(this, """
            buildscript {
                dependencies { classpath(files("${file.normalisedPath}")) }
            }
        """)

    @Test
    fun `can fetch classpath of script plugin`() {

        assertCanFetchClassPathOfScriptPlugin("")
    }

    @Test
    fun `can fetch classpath of script plugin with compilation errors`() {

        assertCanFetchClassPathOfScriptPlugin("val p = ")
    }

    @Test
    fun `can fetch classpath of script plugin with buildscript block compilation errors`() {

        assertCanFetchClassPathOfScriptPlugin("buildscript { val p = }")
    }

    private
    fun assertCanFetchClassPathOfScriptPlugin(scriptPluginCode: String) {
        withBuildSrc()

        val buildSrcDependency =
            withFile("buildSrc-dependency.jar")

        withFile("buildSrc/build.gradle", """
            dependencies { compile(files("../${buildSrcDependency.name}")) }
        """)

        val rootProjectDependency = withFile("rootProject-dependency.jar")

        withFile("build.gradle", """
            buildscript {
                dependencies { classpath(files("${rootProjectDependency.name}")) }
            }
        """)

        val scriptPlugin = withFile("plugin.gradle.kts", scriptPluginCode)

        val scriptPluginClassPath = canonicalClassPathFor(projectRoot, scriptPlugin)
        assertThat(
            scriptPluginClassPath.map { it.name },
            allOf(
                not(hasItem(rootProjectDependency.name)),
                hasItem(buildSrcDependency.name)
            )
        )
        assertContainsBuildSrc(scriptPluginClassPath)
        assertContainsGradleKotlinDslJars(scriptPluginClassPath)
    }

    @Test
    fun `can fetch classpath of script plugin with buildscript block`() {

        val scriptPluginDependency =
            withFile("script-plugin-dependency.jar")

        val scriptPlugin = withFile("plugin.gradle.kts", """
            buildscript {
                dependencies { classpath(files("${scriptPluginDependency.name}")) }
            }

            // Shouldn't be evaluated
            throw IllegalStateException()
        """)

        val model = kotlinBuildScriptModelFor(projectRoot, scriptPlugin)
        assertThat(
            "Script body shouldn't be evaluated",
            model.exceptions,
            equalTo(emptyList()))

        val scriptPluginClassPath = model.canonicalClassPath
        assertThat(
            scriptPluginClassPath.map { it.name },
            hasItem(scriptPluginDependency.name))

        assertContainsGradleKotlinDslJars(scriptPluginClassPath)
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

        assertThat(sourcePathFor(subProjectScriptFile).map { it.name }, matches)
    }
}
