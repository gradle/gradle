package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.concurrent.future

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.matching

import org.gradle.kotlin.dsl.resolver.GradleInstallation
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptModelRequest
import org.gradle.kotlin.dsl.resolver.fetchKotlinBuildScriptModelFor

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.hamcrest.CoreMatchers.*

import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat

import java.io.File


/**
 * Base class for [KotlinBuildScriptModel] integration tests.
 */
abstract class ScriptModelIntegrationTest : AbstractKotlinIntegrationTest() {

    protected
    fun sourcePathFor(scriptFile: File) =
        kotlinBuildScriptModelFor(scriptFile).sourcePath

    protected
    class ProjectSourceRoots(val projectDir: File, val sourceSets: List<String>, val languages: List<String>)

    protected
    fun withMainSourceSetJavaIn(projectDir: String) =
        ProjectSourceRoots(existing(projectDir), listOf("main"), listOf("java"))

    protected
    fun withMainSourceSetJavaKotlinIn(projectDir: String) =
        ProjectSourceRoots(existing(projectDir), listOf("main"), listOf("java", "kotlin"))

    protected
    fun matchesProjectsSourceRoots(vararg projectSourceRoots: ProjectSourceRoots): Matcher<Iterable<File>> {

        fun hasLanguageDir(base: File, set: String, lang: String): Matcher<Iterable<*>> =
            hasItem(base.resolve("src/$set/$lang"))

        return allOf(
            *projectSourceRoots
                .filter { it.languages.isNotEmpty() }
                .flatMap { sourceRoots ->
                    val languageDirs =
                        sourceRoots.sourceSets.flatMap { sourceSet ->
                            listOf("java", "kotlin").map { language ->
                                val hasLanguageDir = hasLanguageDir(sourceRoots.projectDir, sourceSet, language)
                                if (language in sourceRoots.languages) hasLanguageDir
                                else not(hasLanguageDir)
                            }
                        }

                    val resourceDirs =
                        sourceRoots.sourceSets.map { sourceSet ->
                            hasLanguageDir(sourceRoots.projectDir, sourceSet, "resources")
                        }

                    languageDirs + resourceDirs
                }.toTypedArray())
    }

    protected
    fun withMultiProjectKotlinBuildSrc(): Array<ProjectSourceRoots> {
        withSettingsIn("buildSrc", """
            include(":a", ":b", ":c")
        """)
        withFile("buildSrc/build.gradle.kts", """
            plugins {
                java
                `kotlin-dsl` apply false
            }

            val kotlinDslProjects = listOf(project.project(":a"), project.project(":b"))

            kotlinDslProjects.forEach {
                it.apply(plugin = "org.gradle.kotlin.kotlin-dsl")
            }

            dependencies {
                kotlinDslProjects.forEach {
                    "runtime"(project(it.path))
                }
            }
        """)
        withFile("buildSrc/b/build.gradle.kts", """dependencies { implementation(project(":c")) }""")
        withFile("buildSrc/c/build.gradle.kts", "plugins { java }")

        return arrayOf(
            withMainSourceSetJavaIn("buildSrc"),
            withMainSourceSetJavaKotlinIn("buildSrc/a"),
            withMainSourceSetJavaKotlinIn("buildSrc/b"),
            withMainSourceSetJavaIn("buildSrc/c"))
    }

    protected
    fun assertContainsGradleKotlinDslJars(classPath: List<File>) {
        val version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.map { it.name },
            hasItems(
                matching("gradle-kotlin-dsl-$version\\.jar"),
                matching("gradle-api-$version\\.jar"),
                matching("gradle-kotlin-dsl-extensions-$version\\.jar")))
    }

    protected
    fun assertClassPathFor(
        buildScript: File,
        includes: Set<File>,
        excludes: Set<File>,
        importedProjectDir: File = projectRoot
    ) {
        val includeItems = hasItems(*includes.map { it.name }.toTypedArray())
        val excludeItems = not(hasItems(*excludes.map { it.name }.toTypedArray()))
        val condition = if (excludes.isEmpty()) includeItems else allOf(includeItems, excludeItems)
        assertThat(
            classPathFor(buildScript, importedProjectDir).map { it.name },
            condition
        )
    }

    protected
    fun assertClassPathContains(classPath: List<File>, vararg files: File) =
        assertThat(
            classPath.map { it.name },
            hasItems(*fileNameSetOf(*files)))

    protected
    fun assertContainsBuildSrc(classPath: List<File>) =
        assertThat(
            classPath.map { it.name },
            hasBuildSrc())

    protected
    fun hasBuildSrc() =
        hasItem("buildSrc.jar")

    protected
    fun assertIncludes(classPath: List<File>, vararg files: File) =
        assertThat(
            classPath.map { it.name },
            hasItems(*fileNameSetOf(*files)))

    protected
    fun assertExcludes(classPath: List<File>, vararg files: File) =
        assertThat(
            classPath.map { it.name },
            not(hasItems(*fileNameSetOf(*files))))

    private
    fun fileNameSetOf(vararg files: File) =
        files.map { it.name }.toSet().toTypedArray().also {
            assert(it.size == files.size)
        }

    protected
    fun withJar(named: String): File =
        withClassJar(named, DeepThought::class.java)

    internal
    fun canonicalClassPathFor(scriptFile: File, projectDir: File = projectRoot) =
        kotlinBuildScriptModelFor(scriptFile, projectDir).canonicalClassPath

    protected
    fun classPathFor(scriptFile: File, projectDir: File = projectRoot) =
        kotlinBuildScriptModelFor(scriptFile, projectDir).classPath

    internal
    val KotlinBuildScriptModel.canonicalClassPath
        get() = classPath.map(File::getCanonicalFile)

    internal
    fun kotlinBuildScriptModelFor(scriptFile: File, importedProjectDir: File = projectRoot): KotlinBuildScriptModel =
        future {
            fetchKotlinBuildScriptModelFor(
                KotlinBuildScriptModelRequest(
                    projectDir = importedProjectDir,
                    scriptFile = scriptFile,
                    gradleInstallation = testGradleInstallation(),
                    gradleUserHome = buildContext.gradleUserHomeDir
                )
            ) {

                setStandardOutput(System.out)
                setStandardError(System.err)
            }
        }.get()

    private
    fun testGradleInstallation() =
        GradleInstallation.Local(distribution.gradleHomeDir)
}
