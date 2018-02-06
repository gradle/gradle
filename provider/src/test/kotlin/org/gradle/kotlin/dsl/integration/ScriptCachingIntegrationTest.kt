package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinInitScript
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.IsolatedTestKitDir
import org.gradle.kotlin.dsl.fixtures.LeaksFileHandles

import org.gradle.kotlin.dsl.support.KotlinBuildscriptBlock
import org.gradle.kotlin.dsl.support.KotlinInitscriptBlock
import org.gradle.kotlin.dsl.support.KotlinPluginsBlock
import org.gradle.kotlin.dsl.support.KotlinSettingsBuildscriptBlock

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.internal.DefaultGradleRunner

import org.junit.ClassRule
import org.junit.Test

import java.io.File

import kotlin.reflect.KClass


@LeaksFileHandles("""
    Daemons hold their daemon log file open after the build has finished, debug logging exacerbates this.
    This should be revisited once TestKit provides a mechanism to control daemon termination.
""")
class ScriptCachingIntegrationTest : AbstractIntegrationTest() {

    companion object {

        @Suppress("unused")
        @get:ClassRule
        @JvmStatic
        val isolatedTestKitDir = IsolatedTestKitDir()
    }

    @Test
    fun `same script, target type & classpath`() {

        // given: multi-project build with same build files
        val sameContent = """println("Same script content on ${'$'}this")"""
        withMultiProjectBuild(left = sameContent, right = sameContent).apply {

            // when: first use
            buildForCacheInspection("help").apply {

                // then: single compilation and classloading
                compilationCache {
                    misses(settingsFile, rootBuildFile, leftBuildFile)
                    hits(rightBuildFile)
                }
                classLoadingCache {
                    misses(settingsFile, rootBuildFile, leftBuildFile)
                    hits(rightBuildFile)
                }
            }

            // when: second use
            buildForCacheInspection("help").apply {

                // then: no compilation nor class loading
                compilationCache {
                    hits(leftBuildFile, rootBuildFile, rightBuildFile)
                }
                classLoadingCache {
                    hits(leftBuildFile, rootBuildFile, rightBuildFile)
                }
            }

            // when: other daemon
            buildWithAnotherDaemon("help").apply {

                // then: single class loading only
                compilationCache {
                    hits(leftBuildFile, rootBuildFile, rightBuildFile)
                }
                classLoadingCache {
                    misses(rootBuildFile, leftBuildFile)
                    hits(rightBuildFile)
                }
            }
        }
    }

    @Test
    fun `same script different target type`() {

        // given: same init, settings & build files all applying same script
        val same = withFile("same.gradle.kts", """println("Same script on ${'$'}this")""")
        val sameApply = """apply { from("same.gradle.kts") }"""
        val initScriptFile = withFile("same.init.gradle.kts", sameApply)

        val initializationFile = cachedInitializationFile(initScriptFile)
        val settingsFile = cachedSettingsFile(withSettings(sameApply))
        val buildFile = cachedBuildFile(withBuildScript(sameApply))
        val sameOnGradle = cachedGradleScript(same)
        val sameOnSettings = cachedSettingsScript(same)
        val sameOnProject = cachedProjectScript(same)

        // when: first use
        buildForCacheInspection("help", "-I", initScriptFile.absolutePath).apply {

            // then: compilation and classloading
            compilationCache {
                misses(initializationFile, settingsFile, buildFile)
                misses(sameOnGradle, sameOnSettings, sameOnProject)
            }
            classLoadingCache {
                misses(initializationFile, settingsFile, buildFile)
                misses(sameOnGradle, sameOnSettings, sameOnProject)
            }
        }

        // when: second use
        buildForCacheInspection("help", "-I", initScriptFile.absolutePath).apply {

            // then: no compilation nor class loading
            compilationCache {
                hits(initializationFile, settingsFile, buildFile)
                hits(sameOnGradle, sameOnSettings, sameOnProject)
            }
            classLoadingCache {
                hits(initializationFile, settingsFile, buildFile)
                hits(sameOnGradle, sameOnSettings, sameOnProject)
            }
        }

        // when: other daemon
        buildWithAnotherDaemon("help", "-I", initScriptFile.absolutePath).apply {

            // then: class loading only
            compilationCache {
                hits(initializationFile, settingsFile, buildFile)
                hits(sameOnGradle, sameOnSettings, sameOnProject)
            }
            classLoadingCache {
                misses(initializationFile, settingsFile, buildFile)
                misses(sameOnGradle, sameOnSettings, sameOnProject)
            }
        }
    }

    @Test
    fun `same script & target type different classpath`() {

        // given: different classpath
        withClassJar("left/fixture.jar")
        withClassJar("right/fixture.jar", DeepThought::class.java)

        // and: same script & target type
        val sameContent = """
            buildscript {
                dependencies { classpath(files("fixture.jar")) }
            }
            println("Same content, different classpath on ${'$'}this")
        """
        withMultiProjectBuild(left = sameContent, right = sameContent).apply {

            // when: first use
            buildForCacheInspection("help").apply {

                // then: compilation and classloading
                compilationCache {
                    misses(leftBuildFile)
                    hits(rightBuildFile.buildscript!!) // same buildscript block, target type and classpath
                    misses(rightBuildFile.body) // different classpath
                }
                classLoadingCache {
                    misses(leftBuildFile)
                    hits(rightBuildFile.buildscript!!)
                    misses(rightBuildFile.body)
                }
            }

            // when: second use
            buildForCacheInspection("help").apply {

                // then: no compilation nor class loading
                compilationCache {
                    hits(leftBuildFile, rightBuildFile)
                }
                classLoadingCache {
                    hits(leftBuildFile, rightBuildFile)
                }
            }

            // when: other daemon
            buildWithAnotherDaemon("help").apply {

                // then: class loading only
                compilationCache {
                    hits(leftBuildFile, rightBuildFile)
                }
                classLoadingCache {
                    misses(leftBuildFile)
                    hits(rightBuildFile.buildscript!!)
                    misses(rightBuildFile.body)
                }
            }
        }
    }

    @Test
    fun `in-memory script class loading cache releases memory of unused entries`() {

        // given: buildSrc memory hog
        val myTask = withFile("buildSrc/src/main/groovy/MyTask.groovy", """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                static final byte[] MEMORY_HOG = new byte[64 * 1024 * 1024]
                @TaskAction void runAction0() {}
            }
        """)
        val buildFile = cachedBuildFile(withBuildScript("""task<MyTask>("myTask")"""))

        // expect:
        for (run in 1..4) {
            myTask.writeText(myTask.readText().replace("runAction${run - 1}", "runAction$run"))
            buildWithDaemonHeapSize(256, "MyTask").apply {
                compilationCache { misses(buildFile) }
                classLoadingCache { misses(buildFile) }
            }
        }
    }

    private
    fun buildForCacheInspection(vararg arguments: String): BuildResult =
        gradleRunnerForCacheInspection(*arguments)
            .build()

    private
    fun buildWithAnotherDaemon(vararg arguments: String): BuildResult =
        buildWithDaemonHeapSize(160, *arguments)

    private
    fun buildWithDaemonHeapSize(heapMb: Int, vararg arguments: String): BuildResult =
        gradleRunnerForCacheInspection(*arguments).run {
            (this as DefaultGradleRunner).withJvmArguments("-Xms${heapMb}m", "-Xmx${heapMb}m", "-Dfile.encoding=UTF-8")
        }.build()

    private
    fun gradleRunnerForCacheInspection(vararg arguments: String) =
        gradleRunnerForArguments(*arrayOf("-d") + arguments)

    private
    fun withMultiProjectBuild(settings: String = "", root: String = "", left: String = "", right: String = "") =
        MultiProjectCachedScripts(
            cachedSettingsFile(
                withSettings("""
                    rootProject.name = "${projectRoot.name}" // distinguish settings files
                    $settings
                    include("right", "left")
                """)),
            cachedBuildFile(
                withBuildScript(root),
                root.contains("buildscript {"),
                root.contains("plugins {")),
            cachedBuildFile(
                withBuildScriptIn("left", left),
                left.contains("buildscript {"),
                left.contains("plugins {")),
            cachedBuildFile(
                withBuildScriptIn("right", right),
                right.contains("buildscript {"),
                right.contains("plugins {")))
}


private
data class MultiProjectCachedScripts(
    val settingsFile: CachedScript.WholeFile,
    val rootBuildFile: CachedScript.WholeFile,
    val leftBuildFile: CachedScript.WholeFile,
    val rightBuildFile: CachedScript.WholeFile)


private
sealed class CachedScript {

    class WholeFile(
        val buildscript: CompilationStage? = null,
        val plugins: CompilationStage? = null,
        val body: CompilationStage) : CachedScript() {

        val stages = listOfNotNull(buildscript, plugins, body)
    }

    class CompilationStage(
        sourceDescription: String, file: File,
        templateClass: KClass<*>, val enabled: Boolean = true) : CachedScript() {

        val source = "$sourceDescription '$file'"
        val template = templateClass.simpleName!!
    }
}


private
object Descriptions {
    val initializationScript = "initialization script"
    val settingsFile = "settings file"
    val buildFile = "build file"
    val script = "script"
    val initscriptBlock = "initscript block"
    val buildscriptBlock = "buildscript block"
    val pluginsBlock = "plugins block"
}


private
fun cachedInitializationFile(file: File, initscript: Boolean = false) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.initscriptBlock, file, KotlinInitscriptBlock::class, initscript),
        body = CachedScript.CompilationStage(Descriptions.initializationScript, file, KotlinInitScript::class))


private
fun cachedGradleScript(file: File) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.initscriptBlock, file, KotlinInitscriptBlock::class, false),
        body = CachedScript.CompilationStage(Descriptions.script, file, KotlinInitScript::class))


private
fun cachedSettingsFile(file: File, buildscript: Boolean = false) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.buildscriptBlock, file, KotlinSettingsBuildscriptBlock::class, buildscript),
        body = CachedScript.CompilationStage(Descriptions.settingsFile, file, KotlinSettingsScript::class))


private
fun cachedSettingsScript(file: File) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.buildscriptBlock, file, KotlinSettingsBuildscriptBlock::class, false),
        body = CachedScript.CompilationStage(Descriptions.script, file, KotlinSettingsScript::class))


private
fun cachedBuildFile(file: File, buildscript: Boolean = false, plugins: Boolean = false) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.buildscriptBlock, file, KotlinBuildscriptBlock::class, buildscript),
        plugins = CachedScript.CompilationStage(Descriptions.pluginsBlock, file, KotlinPluginsBlock::class, plugins),
        body = CachedScript.CompilationStage(Descriptions.buildFile, file, KotlinBuildScript::class))


private
fun cachedProjectScript(file: File) =
    CachedScript.WholeFile(
        buildscript = CachedScript.CompilationStage(Descriptions.buildscriptBlock, file, KotlinBuildscriptBlock::class, false),
        plugins = CachedScript.CompilationStage(Descriptions.pluginsBlock, file, KotlinPluginsBlock::class, false),
        body = CachedScript.CompilationStage(Descriptions.script, file, KotlinBuildScript::class))


private
fun BuildResult.compilationCache(action: CompilationCache.() -> Unit) =
    action(CompilationCache(this))


private
class CompilationCache(val result: BuildResult) {

    fun misses(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertCompilations(it, 1) }

    fun hits(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertCompilations(it, 0) }

    fun assertCompilations(cachedScript: CachedScript, count: Int) =
        when (cachedScript) {
            is CachedScript.WholeFile        -> cachedScript.stages.forEach { assertCompilations(it, count) }
            is CachedScript.CompilationStage -> assertCompilations(cachedScript, count)
        }

    fun assertCompilations(stage: CachedScript.CompilationStage, count: Int) =
        result.assertOccurrenceCountOf("compiling", stage, count)
}


private
fun BuildResult.classLoadingCache(action: ClassLoadingCache.() -> Unit) =
    action(ClassLoadingCache(this))


private
class ClassLoadingCache(val result: BuildResult) {

    fun misses(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertClassLoads(it, 1) }

    fun hits(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertClassLoads(it, 0) }

    fun assertClassLoads(cachedScript: CachedScript, count: Int) =
        when (cachedScript) {
            is CachedScript.WholeFile        -> cachedScript.stages.forEach { assertClassLoads(it, count) }
            is CachedScript.CompilationStage -> assertClassLoads(cachedScript, count)
        }

    fun assertClassLoads(stage: CachedScript.CompilationStage, count: Int) =
        result.assertOccurrenceCountOf("loading", stage, count)
}


private
fun BuildResult.assertOccurrenceCountOf(actionDisplayName: String, stage: CachedScript.CompilationStage, count: Int) {
    val expectedCount = if (stage.enabled) count else 0
    val logStatement = "${actionDisplayName.capitalize()} ${stage.template} from ${stage.source}"
    val observedCount = output.occurrenceCountOf(logStatement)
    require(observedCount == expectedCount) {
        "Expected $actionDisplayName $expectedCount ${stage.template} from ${stage.source}, but got $observedCount\n" +
            "  Looking for statement: $logStatement\n" +
            "  Build output was:\n" + output.prependIndent("    ")
    }
}


private
fun String.occurrenceCountOf(string: String) =
    split(string).size - 1
