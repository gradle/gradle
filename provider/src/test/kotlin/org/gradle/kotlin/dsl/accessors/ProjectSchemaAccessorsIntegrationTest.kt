package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.integration.kotlinBuildScriptModelFor

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.fileByName
import org.gradle.kotlin.dsl.fixtures.matching

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
                api("com.google.guava:guava:21.0")
            }

            publishing {
                publications.create<MavenPublication>("mavenJavaLibrary") {
                    from(components["java"])
                }
            }

            dependencies {
                api("org.apache.commons:commons-lang3:3.5")
            }

        """)

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
            build("kotlinDslAccessorsSnapshot").output)


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
    fun `can access extensions registered by declared plugins via jit accessor`() {

        withBuildScript("""
            plugins { application }

            application { mainClassName = "App" }

            task("mainClassName") {
                doLast { println("*" + application.mainClassName + "*") }
            }
        """)

        assertThat(
            build("mainClassName").output,
            containsString("*App*"))
    }

    @Test
    fun `can access configurations registered by declared plugins via jit accessor`() {

        withSettingsScript("""
            include(arrayOf("a", "b", "c"))
        """)

        withBuildScriptIn("a", """
            plugins { `java-library` }
        """)

        withBuildScriptIn("b", """
            plugins { `java-library` }

            dependencies {
                compile("org.apache.commons:commons-io:1.3.2")
            }

            repositories { jcenter() }
        """)

        withBuildScriptIn("c", """
            plugins { `java-library` }

            dependencies {
                compileOnly(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")
                api("com.google.guava:guava:21.0")
                implementation("ch.qos.logback:logback-classic:1.2.3") {
                    isTransitive = false
                }
                implementation(project(":a"))
                implementation(project(":b")) {
                    exclude(group = "org.apache.commons")
                }
            }

            repositories { jcenter() }

            configurations.compileClasspath.files.forEach {
                println(org.gradle.util.TextUtil.normaliseFileSeparators(it.path))
            }
        """)

        val result = build("help", "-q")

        assertThat(
            result.output,
            allOf(
                containsString("slf4j-api-1.7.25.jar"),
                containsString("guava-21.0.jar"),
                containsString("logback-classic-1.2.3.jar"),
                containsString("a/build/classes/java/main"),
                containsString("b/build/classes/java/main"),
                not(containsString("logback-core")),
                not(containsString("commons-io"))))
    }

    @Test
    fun `classpath model includes generated accessors`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        println(
            build("kotlinDslAccessorsSnapshot").output)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `classpath model includes jit accessors by default`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `jit accessors can be turned off`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        withFile("gradle.properties", "org.gradle.kotlin.dsl.accessors=off")

        assertThat(
            classPathFor(buildFile),
            not(hasAccessorsJar()))
    }

    @Test
    fun `the set of jit accessors is a function of the set of applied plugins`() {

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

        withSettingsScript("include(arrayOf(\"a\"))")
        withBuildScriptIn("a", "")

        val aTasks = build(":a:tasks").output
        assertThat(aTasks, containsString("kotlinDslAccessorsReport"))
        assertThat(aTasks, not(containsString("kotlinDslAccessorsSnapshot")))

        val rootTasks = build(":tasks").output
        assertThat(rootTasks, allOf(containsString("kotlinDslAccessorsReport"), containsString("kotlinDslAccessorsSnapshot")))
    }

    @Test
    fun `given extension with inaccessible type, its accessor is typed Any`() {

        withFile("init.gradle", """
            initscript {
                repositories {
                    maven { url "https://plugins.gradle.org/m2" }
                }
                dependencies {
                    classpath "com.gradle:build-scan-plugin:1.8"
                }
            }
            rootProject {
                apply plugin: "base"
                apply plugin: initscript.classLoader.loadClass("com.gradle.scan.plugin.BuildScanPlugin")
                buildScan {
                    publishAlways()
                }
            }
        """)

        withBuildScript("""

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            buildScan {
                println("Type of `buildScan` receiver is " + typeOf(this@buildScan))
            }
        """)

        val result = build("help", "-I", "init.gradle")
        assertThat(result.output, containsString("Type of `buildScan` receiver is Any"))
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
                File(this, "org/gradle/kotlin/dsl/accessors.kt").isFile
            })

    private
    fun hasAccessorsJar() =
        hasItem(fileByName(accessorsJarFileName))

    private
    fun accessorsJarFor(buildFile: File) =
        classPathFor(buildFile)
            .find { it.isFile && it.name == accessorsJarFileName }

    private
    val accessorsJarFileName = "gradle-kotlin-dsl-accessors.jar"

    private
    fun classPathFor(buildFile: File) =
        kotlinBuildScriptModelFor(buildFile).classPath

    private
    fun kotlinBuildScriptModelFor(buildFile: File) =
        kotlinBuildScriptModelFor(projectRoot, buildFile)
}
