package org.gradle.script.lang.kotlin.accessors

import org.gradle.script.lang.kotlin.integration.kotlinBuildScriptModelFor

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.fixtures.fileByName
import org.gradle.script.lang.kotlin.fixtures.matching

import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class ProjectSchemaAccessorsIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can configure deferred configurable extension`() {

        withBuildScript("""

            import org.gradle.api.publish.maven.MavenPublication

            plugins {
                `java-library`
                `maven-publish`
            }

            dependencies {
                "api"("com.google.guava:guava:21.0")
            }

            publishing {
                publications.create<MavenPublication>("mavenJavaLibrary") {
                    from(components["java"])
                }
            }

            dependencies {
                "api"("org.apache.commons:commons-lang3:3.5")
            }

        """)

        withAutomaticAccessors()

        build("generatePom")

        val pom = existing("build/publications/mavenJavaLibrary/pom-default.xml").readText()
        assertThat(pom, containsString("com.google.guava"))
        assertThat(pom, containsString("commons-lang3"))
    }

    @Test
    fun `can access NamedDomainObjectContainer extension via generated accessor`() {

        withKotlinBuildSrc()

        withFile("buildSrc/src/main/kotlin/my/DocumentationPlugin.kt", """
            package my

            import org.gradle.api.*

            class DocumentationPlugin : Plugin<Project> {

                override fun apply(project: Project) {
                    val books = project.container(Book::class.java, ::Book)
                    project.extensions.add("the books", books)
                }
            }

            data class Book(val name: String)

        """)

        val buildFile = withBuildScript("""

            apply<my.DocumentationPlugin>()

        """)


        println(
            build("gskGenerateAccessors").output)


        buildFile.appendText("""

            (`the books`) {
                "quickStart" {
                }
                "userGuide" {
                }
            }

            tasks {
                "books" {
                    doLast { println(`the books`.joinToString { it.name }) }
                }
            }

        """)
        assertThat(
            build("books").output,
            containsString("quickStart, userGuide"))
    }

    @Test
    fun `can access extensions registered by declared plugins via automatic accessor`() {

        withBuildScript("""
            plugins { application }

            application { mainClassName = "App" }

            task("mainClassName") {
                doLast { println("*" + application.mainClassName + "*") }
            }
        """)

        withAutomaticAccessors()
        assertThat(
            build("mainClassName").output,
            containsString("*App*"))
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
    fun `classpath model includes automatic accessors`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        withAutomaticAccessors()
        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `classpath model does not include automatic accessors by default`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        assertThat(
            classPathFor(buildFile),
            not(hasAccessorsJar()))
    }

    @Test
    fun `the set of automatic accessors is a function of the set of applied plugins`() {

        withAutomaticAccessors()

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

    @Test
    fun `accessors tasks applied in a mixed Groovy-Kotlin multi-project build`() {

        withFile("settings.gradle", """
            include 'a'
        """)
        withFile("a/build.gradle.kts")

        val aTasks = build(":a:tasks").output
        assertThat(aTasks, containsString("gskProjectAccessors"))
        assertThat(aTasks, not(containsString("gskGenerateAccessors")))

        val rootTasks = build(":tasks").output
        assertThat(rootTasks, allOf(containsString("gskProjectAccessors"), containsString("gskGenerateAccessors")))
    }

    private
    fun withAutomaticAccessors() {
        withFile("gradle.properties", "org.gradle.script.lang.kotlin.accessors.auto=true")
    }

    private
    fun setOfAutomaticAccessorsFor(plugins: Set<String>): File {
        val script = "plugins {\n${plugins.joinToString(separator = "\n")}\n}"
        val buildFile = withBuildScript(script, produceFile = this::newOrExisting)
        return accessorsJarFor(buildFile)!!.relativeTo(buildFile.parentFile)
    }

    private
    fun assertAccessorsInClassPathOf(buildFile: File) {
        val model = kotlinBuildScriptModelFor(buildFile)
        assertThat(model.classPath, hasAccessorsJar())
        assertThat(model.sourcePath, hasAccessorsSource())
    }

    private
    fun hasAccessorsSource() =
        hasItem(
            matching<File>({ appendText("accessors source") }) {
                File(this, "org/gradle/script/lang/kotlin/accessors.kt").isFile
            })

    private
    fun hasAccessorsJar() =
        hasItem(fileByName("gradle-kotlin-dsl-accessors.jar"))

    private
    fun accessorsJarFor(buildFile: File) =
        classPathFor(buildFile)
            .find { it.isFile && it.name == "gradle-kotlin-dsl-accessors.jar" }

    private
    fun classPathFor(buildFile: File) =
        kotlinBuildScriptModelFor(buildFile).classPath

    private
    fun kotlinBuildScriptModelFor(buildFile: File) =
        kotlinBuildScriptModelFor(projectRoot, buildFile)
}
