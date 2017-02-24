package org.gradle.script.lang.kotlin.accessors

import org.gradle.script.lang.kotlin.integration.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.integration.canonicalClassPathFor

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

class ProjectSchemaAccessorsIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can access NamedDomainObjectContainer extension via generated accessor`() {

        val buildFile = withBuildScript("""

            apply {
                plugin<DocumentationPlugin>()
            }

            class DocumentationPlugin : Plugin<Project> {

                override fun apply(project: Project) {
                    val books = project.container(Book::class.java, ::Book)
                    project.extensions.add("books", books)
                }
            }

            data class Book(val name: String)

        """)


        println(
            build("gskGenerateAccessors").output)


        buildFile.appendText("""

            (books()) {
                "quickStart" {
                }
                "userGuide" {
                }
            }

            tasks {
                "books" {
                    doLast { println(books().joinToString { it.name }) }
                }
            }

        """)
        assertThat(
            build("books").output,
            containsString("quickStart, userGuide"))
    }

    @Test
    fun `classpath model includes generated accessors`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        println(
            build("gskGenerateAccessors").output)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `can access extensions registered by declared plugins via automatic accessor`() {

        withBuildScript("""
            plugins { application }

            application { mainClassName = "App" }

            task("mainClassName") {
                doLast { println("*" + application().mainClassName + "*") }
            }
        """)

        assertThat(
            build("mainClassName").output,
            containsString("*App*"))
    }

    @Test
    fun `classpath model includes automatic accessors`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `the set of automatic accessors is a function of the set of applied plugins`() {

        val s1 = setOfAutomaticAccessorsFor(setOf("application"))
        val s2 = setOfAutomaticAccessorsFor(setOf("java"))
        val s3 = setOfAutomaticAccessorsFor(setOf("application"))
        val s4 = setOfAutomaticAccessorsFor(setOf("application", "java"))
        val s5 = setOfAutomaticAccessorsFor(setOf("java"))

        assertThat(s1, not(equalTo(s2))) // application ≠ java
        assertThat(s1, equalTo(s3))      // application = application
        assertThat(s2, equalTo(s5))      // java        = java
        assertThat(s1, equalTo(s4))      // application ⊇ java
    }

    private fun setOfAutomaticAccessorsFor(plugins: Set<String>): Set<File> {
        val script = "plugins {\n${plugins.joinToString(separator = "\n")}\n}"
        val buildFile = withBuildScript(script, produceFile = this::newOrExisting)
        return accessorClassFilesFor(buildFile)
    }

    private fun accessorClassFilesFor(buildFile: File): Set<File> =
        accessorsClassPathFor(buildFile)!!.let { baseDir ->
            classFilesIn(baseDir).map { it.relativeTo(baseDir) }.toSet()
        }

    private fun classFilesIn(baseDir: File) =
        baseDir.walkTopDown().filter { it.isFile && it.extension == "class" }

    private fun assertAccessorsInClassPathOf(buildFile: File) {
        assert(accessorsClassPathFor(buildFile) != null)
    }

    private fun accessorsClassPathFor(buildFile: File) =
        canonicalClassPathFor(projectRoot, buildFile)
            .find { isAccessorsClassPath(it) }

    private fun isAccessorsClassPath(it: File) =
        it.isDirectory && File(it, "org/gradle/script/lang/kotlin/__accessorsKt.class").isFile
}
