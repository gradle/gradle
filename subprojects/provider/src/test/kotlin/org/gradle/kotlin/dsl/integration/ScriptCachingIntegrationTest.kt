package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.execution.Program
import org.gradle.kotlin.dsl.execution.ProgramKind
import org.gradle.kotlin.dsl.execution.ProgramKind.ScriptPlugin
import org.gradle.kotlin.dsl.execution.ProgramKind.TopLevel
import org.gradle.kotlin.dsl.execution.ProgramParser
import org.gradle.kotlin.dsl.execution.ProgramSource
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.execution.ProgramTarget.Gradle
import org.gradle.kotlin.dsl.execution.ProgramTarget.Project
import org.gradle.kotlin.dsl.execution.ProgramTarget.Settings
import org.gradle.kotlin.dsl.execution.templateIdFor

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.IsolatedTestKitDir
import org.gradle.kotlin.dsl.fixtures.LeaksFileHandles

import org.gradle.testkit.runner.BuildResult

import org.junit.ClassRule
import org.junit.Test

import java.io.File


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
        val sameApply = """apply(from = "same.gradle.kts")"""
        val initScriptFile = withFile("same.init.gradle.kts", sameApply)

        val initializationFile = cachedInitializationFile(initScriptFile, false, true)
        val settingsFile = cachedSettingsFile(withSettings(sameApply), false, true)
        val buildFile = cachedBuildFile(withBuildScript(sameApply), true)
        val sameOnGradle = cachedGradleScript(same, false, true)
        val sameOnSettings = cachedSettingsScript(same, false, true)
        val sameOnProject = cachedProjectScript(same, false, true)

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
                    hits(rightBuildFile.stage1) // same buildscript block, target type and classpath
                    misses(rightBuildFile.stage2) // different classpath
                }
                classLoadingCache {
                    misses(leftBuildFile)
                    hits(rightBuildFile.stage1)
                    misses(rightBuildFile.stage2)
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
                    hits(rightBuildFile.stage1)
                    misses(rightBuildFile.stage2)
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
        val settingsFile = cachedSettingsFile(withSettings(""), false, false)
        val buildFile = cachedBuildFile(withBuildScript("""task<MyTask>("myTask")"""), true)

        // expect:
        for (run in 1..4) {
            myTask.writeText(myTask.readText().replace("runAction${run - 1}", "runAction$run"))
            buildWithDaemonHeapSize(256, "MyTask").apply {
                compilationCache {
                    misses(settingsFile.stage1)
                    hits(settingsFile.stage2)
                    misses(buildFile)
                }
                classLoadingCache {
                    misses(settingsFile.stage1)
                    hits(settingsFile.stage2)
                    misses(buildFile)
                }
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
        withGradleJvmArguments("-Xms${heapMb}m", "-Xmx${heapMb}m", "-Dfile.encoding=UTF-8").run {
            gradleRunnerForCacheInspection(*arguments).build()
        }

    private
    fun gradleRunnerForCacheInspection(vararg arguments: String) =
        gradleRunnerForArguments(*arrayOf("-d") + arguments)

    private
    fun withMultiProjectBuild(settings: String = "", root: String = "", left: String = "", right: String = "") =
        MultiProjectCachedScripts(
            cachedSettingsFile(
                withSettings("""
                    $settings
                    rootProject.name = "${projectRoot.name}" // distinguish settings files
                    include("right", "left")
                """),
                settings.contains("buildscript {"),
                true),
            cachedBuildFile(
                withBuildScript(root),
                hasBody(root)),
            cachedBuildFile(
                withBuildScriptIn("left", left),
                hasBody(left)),
            cachedBuildFile(
                withBuildScriptIn("right", right),
                hasBody(right)))
}


private
fun hasBody(script: String) =
    ProgramParser.parse(ProgramSource("script.gradle.kts", script), TopLevel, ProgramTarget.Project).let {
        it is Program.Script || it is Program.Staged
    }


private
data class MultiProjectCachedScripts(
    val settingsFile: CachedScript.WholeFile,
    val rootBuildFile: CachedScript.WholeFile,
    val leftBuildFile: CachedScript.WholeFile,
    val rightBuildFile: CachedScript.WholeFile
)


private
sealed class CachedScript {

    class WholeFile(
        val stage1: CompilationStage,
        val stage2: CompilationStage
    ) : CachedScript() {

        val stages = listOf(stage1, stage2)
    }

    class CompilationStage(
        programTarget: ProgramTarget,
        programKind: ProgramKind,
        stage: String,
        sourceDescription: String,
        file: File,
        val enabled: Boolean = true
    ) : CachedScript() {

        val source = "$sourceDescription '$file'"
        val templateId = templateIdFor(programTarget, programKind, stage)
    }
}


private
object Descriptions {
    const val initializationScript = "initialization script"
    const val settingsFile = "settings file"
    const val buildFile = "build file"
    const val script = "script"
}


private
fun cachedInitializationFile(file: File, hasInitscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Gradle, TopLevel, "stage1", Descriptions.initializationScript, file),
        stage2 = CachedScript.CompilationStage(Gradle, TopLevel, "stage2", Descriptions.initializationScript, file, hasInitscriptBlock && hasBody)
    )


private
fun cachedGradleScript(file: File, hasInitscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Gradle, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Gradle, ScriptPlugin, "stage2", Descriptions.script, file, hasInitscriptBlock && hasBody)
    )


private
fun cachedSettingsFile(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Settings, TopLevel, "stage1", Descriptions.settingsFile, file),
        stage2 = CachedScript.CompilationStage(Settings, TopLevel, "stage2", Descriptions.settingsFile, file, hasBuildscriptBlock && hasBody)
    )


private
fun cachedSettingsScript(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Settings, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Settings, ScriptPlugin, "stage2", Descriptions.script, file, hasBuildscriptBlock && hasBody)
    )


private
fun cachedBuildFile(file: File, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Project, TopLevel, "stage1", Descriptions.buildFile, file),
        stage2 = CachedScript.CompilationStage(Project, TopLevel, "stage2", Descriptions.buildFile, file, hasBody)
    )


private
fun cachedProjectScript(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Project, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Project, ScriptPlugin, "stage2", Descriptions.script, file, hasBuildscriptBlock && hasBody)
    )


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
            is CachedScript.WholeFile -> cachedScript.stages.forEach { assertCompilations(it, count) }
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
            is CachedScript.WholeFile -> cachedScript.stages.forEach { assertClassLoads(it, count) }
            is CachedScript.CompilationStage -> assertClassLoads(cachedScript, count)
        }

    fun assertClassLoads(stage: CachedScript.CompilationStage, count: Int) =
        result.assertOccurrenceCountOf("loading", stage, count)
}


private
fun BuildResult.assertOccurrenceCountOf(actionDisplayName: String, stage: CachedScript.CompilationStage, count: Int) {
    val expectedCount = if (stage.enabled) count else 0
    val logStatement = "${actionDisplayName.capitalize()} ${stage.templateId} from ${stage.source}"
    val observedCount = output.occurrenceCountOf(logStatement)
    require(observedCount == expectedCount) {
        "Expected $actionDisplayName $expectedCount ${stage.templateId} from ${stage.source}, but got $observedCount\n" +
            "  Looking for statement: $logStatement\n" +
            "  Build output was:\n" + output.prependIndent("    ")
    }
}


private
fun String.occurrenceCountOf(string: String) =
    split(string).size - 1
