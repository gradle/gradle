package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.embeddedKotlinVersion
import org.gradle.script.lang.kotlin.integration.fixture.DeepThought

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Ignore
import org.junit.Test

import java.io.File

import kotlin.test.assertNotEquals

class GradleScriptKotlinIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `given a buildscript block, it will be used to compute the runtime classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("fixture.jar"))
                }
            }

            task("compute") {
                doLast {
                    // resources.jar should be in the classpath
                    val computer = ${DeepThought::class.qualifiedName}()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """)

        assert(
            build("compute").output.contains("*42*"))
    }

    @Test
    fun `given a buildSrc dir, it will be added to the compilation classpath`() {

        withFile("buildSrc/src/main/groovy/build/DeepThought.groovy", """
            package build
            class DeepThought {
                def compute() { 42 }
            }
        """)

        withBuildScript("""
            task("compute") {
                doLast {
                    val computer = build.DeepThought()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """)

        assert(
            build("compute").output.contains("*42*"))
    }

    @Test
    fun `given a Kotlin project in buildSrc, it will be added to the compilation classpath`() {

        withFile("buildSrc/src/main/kotlin/build/DeepThought.kt", """
            package build

            class DeepThought() {
                fun compute(handler: (Int) -> Unit) { handler(42) }
            }
        """)

        withFile("buildSrc/src/main/kotlin/build/DeepThoughtPlugin.kt", """
            package build

            import org.gradle.api.*
            import org.gradle.script.lang.kotlin.*

            open class DeepThoughtPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    with (project) {
                        task("compute") {
                            doLast {
                                DeepThought().compute { answer ->
                                    println("*" + answer + "*")
                                }
                            }
                        }
                    }
                }
            }
        """)

        withBuildScriptIn("buildSrc", """
            buildscript {
                configure(listOf(repositories, project.repositories)) {
                    gradleScriptKotlin()
                }
                dependencies {
                    classpath(kotlinModule("gradle-plugin"))
                }
            }
            apply {
                plugin("kotlin")
            }
            dependencies {
                compile(gradleScriptKotlinApi())
            }
        """)

        withBuildScript("""
            buildscript {
                // buildSrc types are available within buildscript
                // and must always be fully qualified
                build.DeepThought().compute { answer ->
                    println("buildscript: " + answer)
                }
            }
            apply<build.DeepThoughtPlugin>()
        """)

        val output = build("compute").output
        assert(output.contains("buildscript: 42"))
        assert(output.contains("*42*"))
    }

    @Test
    @Ignore("See #189")
    fun `given a plugin compiled against Kotlin one dot zero, it will run against the embedded Kotlin version`() {

        withBuildScript("""
            buildscript {
                repositories {
                    ivy { setUrl("${fixturesRepository.toURI()}") }
                    jcenter()
                }
                dependencies {
                    classpath("org.gradle.script.lang.kotlin.fixtures:plugin-compiled-against-kotlin-1.0:1.0")
                }
            }

            apply<fixtures.ThePlugin>()

            tasks.withType<fixtures.ThePluginTask> {
                from = "new value"
                doLast {
                    println(configure { "*[" + it + "]*" })
                }
            }
        """)

        assert(
            build("the-plugin-task").output.contains("*[new value]*"))
    }

    @Test
    fun `can compile against a different (but compatible) version of the Kotlin compiler`() {

        val differentKotlinVersion = "1.1.0-dev-1159"
        assertNotEquals(embeddedKotlinVersion, differentKotlinVersion)

        withBuildScript("""
            import org.jetbrains.kotlin.cli.common.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            buildscript {
                repositories {
                    gradleScriptKotlin()
                }
                dependencies {
                    classpath(kotlinModule("gradle-plugin", version = "$differentKotlinVersion"))
                }
            }

            apply { plugin("kotlin") }

            tasks.withType<KotlinCompile> {
                // can configure the Kotlin compiler
                kotlinOptions.noCallAssertions = true
            }

            task("print-kotlin-version") {
                doLast {
                    val compileOptions = tasks.filterIsInstance<KotlinCompile>().joinToString(prefix="[", postfix="]") {
                        it.name + "=" + it.kotlinOptions.noCallAssertions
                    }
                    println(KotlinVersion.VERSION + compileOptions)
                }
            }
        """)

        assertThat(
            build("print-kotlin-version").output,
            containsString(differentKotlinVersion + "[compileKotlin=true, compileTestKotlin=true]"))
    }

    @Test
    fun `can serve buildSrc classpath in face of compilation errors`() {

        withBuildSrc()

        withBuildScript("""
            val p =
        """)

        assertBuildScriptModelClassPathContains(
            buildSrcOutputFolder())
    }

    @Test
    fun `can serve buildscript classpath in face of compilation errors`() {

        withFile("classes.jar", "")

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }

            val p =
        """)

        assertBuildScriptModelClassPathContains(
            existing("classes.jar"))
    }

    @Test
    fun `can serve buildscript classpath of top level Groovy script`() {

        //
        // Supports code completion on build.gradle.kts files which have not
        // been included in the build for whatever reason.
        //
        // An example would be a conditional `apply from: 'build.gradle.kts'`.
        //

        withBuildSrc()

        withFile("classes.jar", "")

        withFile("build.gradle", """
            buildscript {
                dependencies {
                    classpath(files("classes.jar"))
                }
            }
        """)

        val classPath = kotlinBuildScriptModelCanonicalClassPath()
        assertThat(
            classPath,
            hasItems(
                buildSrcOutputFolder(),
                existing("classes.jar")))

        val version = "[0-9.]+(-.+?)?"
        assertThat(
            classPath.map { it.name },
            hasItems(
                matching("gradle-script-kotlin-$version\\.jar"),
                matching("gradle-script-kotlin-api-$version\\.jar"),
                matching("gradle-script-kotlin-extensions-$version\\.jar")))
    }

    @Test
    fun `can use Closure only APIs`() {

        withBuildScript("""
            gradle.buildFinished(closureOf<org.gradle.BuildResult> {
                println("*" + action + "*") // <- BuildResult.getAction()
            })
        """)

        assert(
            build("build").output.contains("*Build*"))
    }

    @Test
    fun `can use generated API extensions`() {
        withBuildScript("""
            repositories {
                // maven(setup: MavenArtifactRepository.() -> Unit) is a generated extension
                maven { setUrl("https://foo.bar/qux") }
            }
            repositories
                .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
                .forEach { println("url: " + it.url) }
        """)
        assertThat(
            build().output,
            containsString("url: https://foo.bar/qux"))
    }

    @Test
    fun `given an exception thrown during buildscript block execution, its stack trace should contain correct file and line info`() {
        withBuildScript(""" // line 1
            // line 2
            // line 3
            buildscript { // line 4
                throw IllegalStateException() // line 5
            }
        """)
        assertThat(
            buildFailureOutput(),
            containsString("build.gradle.kts:5"))
    }

    private val fixturesRepository: File
        get() = File("fixtures/repository").absoluteFile
}

