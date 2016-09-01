package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.embeddedKotlinVersion
import org.gradle.script.lang.kotlin.integration.fixture.DeepThought
import org.gradle.script.lang.kotlin.support.KotlinBuildScriptModel
import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.retrieveKotlinBuildScriptModelFrom
import org.gradle.script.lang.kotlin.support.zipTo

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

import kotlin.test.assertNotEquals

class GradleScriptKotlinIntegrationTest {

    @JvmField
    @Rule val projectDir = TemporaryFolder()

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

        withBuildScriptIn("buildSrc", """
            buildscript {
                repositories { gradleScriptKotlin() }
                dependencies { classpath(kotlinModule("gradle-plugin")) }
            }
            apply { plugin("kotlin") }
            dependencies { compile(kotlinModule("stdlib")) }
            repositories { gradleScriptKotlin() }
        """)

        withBuildScript("""
            task("compute") {
                doLast {
                    val computer = build.DeepThought()
                    computer.compute { answer ->
                        println("*" + answer + "*")
                    }
                }
            }
        """)

        assert(
            build("compute").output.contains("*42*"))
    }

    @Test
    fun `given a plugin compiled against Kotlin one dot zero, it will run against the embedded Kotlin version`() {

        withBuildScript("""
            buildscript {
                repositories {
                    ivy { setUrl("$fixturesRepository") }
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
            buildSrcOutput())
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
                buildSrcOutput(),
                existing("classes.jar")))

        val version = "[0-9.]+-.+?"
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

    private fun withBuildSrc() {
        withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)
    }

    private fun buildSrcOutput(): File =
        existing("buildSrc/build/classes/main")

    private val fixturesRepository: File
        get() = File("fixtures/repository").absoluteFile

    private fun withBuildScript(script: String) {
        withBuildScriptIn(".", script)
    }

    private fun withBuildScriptIn(baseDir: String, script: String) {
        withFile("$baseDir/settings.gradle", "rootProject.buildFileName = 'build.gradle.kts'")
        withFile("$baseDir/build.gradle.kts", script)
    }

    private fun withFile(fileName: String, text: String) {
        file(fileName).writeText(text)
    }

    private fun withClassJar(fileName: String, vararg classes: Class<*>) =
        zipTo(file(fileName), classEntriesFor(*classes))

    private fun file(fileName: String) =
        projectDir.run {
            makeParentFoldersOf(fileName)
            newFile(fileName)
        }

    private fun existing(relativePath: String) =
        File(projectDir.root, relativePath).run {
            canonicalFile
        }

    private fun TemporaryFolder.makeParentFoldersOf(fileName: String) {
        File(root, fileName).parentFile.mkdirs()
    }

    private fun build(vararg arguments: String): BuildResult =
        gradleRunner()
            .withArguments(*arguments, "--stacktrace")
            .build()

    private fun gradleRunner() =
        gradleRunnerFor(projectDir.root)

    private fun gradleRunnerFor(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withDebug(false)
            .withGradleInstallation(customInstallation())
            .withProjectDir(projectDir)

    private fun assertBuildScriptModelClassPathContains(vararg files: File) {
        assertThat(
            kotlinBuildScriptModelCanonicalClassPath(),
            hasItems(*files))
    }

    private fun kotlinBuildScriptModelCanonicalClassPath() =
        kotlinBuildScriptModel().classPath.map { it.canonicalFile }

    private fun kotlinBuildScriptModel(): KotlinBuildScriptModel =
        withDaemonRegistry(customDaemonRegistry()) {
            retrieveKotlinBuildScriptModelFrom(projectDir.root, customInstallation())!!
        }

    private fun customDaemonRegistry() =
        File("build/custom/daemon-registry")

    private fun customInstallation() =
        File("build/custom").listFiles()?.let {
            it.singleOrNull { it.name.startsWith("gradle") } ?:
                throw IllegalStateException(
                    "Expected 1 custom installation but found ${it.size}. Run `./gradlew clean customInstallation`.")
        } ?: throw IllegalStateException("Custom installation not found. Run `./gradlew customInstallation`.")
}

inline fun <T> withDaemonRegistry(registryBase: File, block: () -> T) =
    withSystemProperty("org.gradle.daemon.registry.base", registryBase.absolutePath, block)

inline fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
    val originalValue = System.getProperty(key)
    try {
        System.setProperty(key, value)
        return block()
    } finally {
        setOrClearProperty(key, originalValue)
    }
}

fun setOrClearProperty(key: String, value: String?) {
    when (value) {
        null -> System.clearProperty(key)
        else -> System.setProperty(key, value)
    }
}
