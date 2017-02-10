package org.gradle.script.lang.kotlin.accessors

import org.gradle.script.lang.kotlin.integration.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class ProjectSchemaAccessorsIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun canAccessNamedDomainObjectContainerExtension() {

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
}
