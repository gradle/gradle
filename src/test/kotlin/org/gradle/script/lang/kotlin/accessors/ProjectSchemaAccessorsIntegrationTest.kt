package org.gradle.script.lang.kotlin.accessors

import org.gradle.script.lang.kotlin.integration.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.integration.canonicalClassPathFor

import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.assertFalse
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

        val buildFile = withBuildScript("""

            apply {
                plugin<DocumentationPlugin>()
            }

            class DocumentationPlugin : Plugin<Project> {

                override fun apply(project: Project) {
                    val books = project.container(Book::class.java, ::Book)
                    project.extensions.add("the books", books)
                }
            }

            data class Book(val name: String)

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
                doLast { println("*" + application.mainClassName + "*") }
            }
        """)

        withAutomaticAccessors()
        assertThat(
            build("mainClassName").output,
            containsString("*App*"))
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

        assertFalse(hasAccessorsInClassPathOf(buildFile))
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
            project(':a').buildFileName = 'build.gradle.kts'
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
    fun setOfAutomaticAccessorsFor(plugins: Set<String>): Set<File> {
        val script = "plugins {\n${plugins.joinToString(separator = "\n")}\n}"
        val buildFile = withBuildScript(script, produceFile = this::newOrExisting)
        return accessorClassFilesFor(buildFile)
    }

    private
    fun accessorClassFilesFor(buildFile: File): Set<File> =
        accessorsClassPathFor(buildFile)!!.let { baseDir ->
            classFilesIn(baseDir).map { it.relativeTo(baseDir) }.toSet()
        }

    private
    fun classFilesIn(baseDir: File) =
        baseDir.walkTopDown().filter { it.isFile && it.extension == "class" }

    private
    fun assertAccessorsInClassPathOf(buildFile: File) {
        assert(hasAccessorsInClassPathOf(buildFile))
    }

    private
    fun hasAccessorsInClassPathOf(buildFile: File) =
        accessorsClassPathFor(buildFile) != null

    private
    fun accessorsClassPathFor(buildFile: File) =
        canonicalClassPathFor(projectRoot, buildFile)
            .find { isAccessorsClassPath(it) }

    private
    fun isAccessorsClassPath(it: File) =
        it.isDirectory && File(it, "org/gradle/script/lang/kotlin/__accessorsKt.class").isFile
}
