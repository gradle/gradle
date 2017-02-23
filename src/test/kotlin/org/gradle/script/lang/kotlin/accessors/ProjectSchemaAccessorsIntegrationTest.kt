package org.gradle.script.lang.kotlin.accessors

import org.gradle.script.lang.kotlin.integration.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.integration.canonicalClassPathFor

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File

class ProjectSchemaAccessorsIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can access NamedDomainObjectContainer extension`() {

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

        assert(
            canonicalClassPathFor(projectRoot, buildFile)
                .any { it.isDirectory && File(it, "org/gradle/script/lang/kotlin/__accessorsKt.class").isFile })
    }
}
