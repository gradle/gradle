/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.caching

import org.gradle.integtests.fixtures.executer.ExecutionResult

import org.gradle.kotlin.dsl.execution.Program
import org.gradle.kotlin.dsl.execution.ProgramKind.TopLevel
import org.gradle.kotlin.dsl.execution.ProgramParser
import org.gradle.kotlin.dsl.execution.ProgramSource
import org.gradle.kotlin.dsl.execution.ProgramTarget

import org.gradle.kotlin.dsl.fixtures.DeepThought

import org.gradle.soak.categories.SoakTest

import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.File
import java.util.UUID


@Category(SoakTest::class)
class ScriptCachingIntegrationTest : AbstractScriptCachingIntegrationTest() {

    @Test
    fun `same script, target type & classpath`() {

        // given: multi-project build with same build files
        val sameContent = """println("Same script content on ${'$'}this")"""
        withMultiProjectBuild(left = sameContent, right = sameContent).apply {

            // when: first use
            buildForCacheInspection("help").apply {
                compilationTrace(projectRoot) {
                    assertScriptCompile(settingsFile.stage1)
                    assertNoScriptCompile(settingsFile.stage2)
                    assertNoScriptCompile(rootBuildFile.stage1)
                    assertScriptCompile(rootBuildFile.stage2)
                }
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

                compilationTrace(projectRoot) {
                    assertNoScriptCompile(settingsFile.stage1)
                    assertNoScriptCompile(settingsFile.stage2)
                    assertNoScriptCompile(rootBuildFile.stage1)
                    assertNoScriptCompile(rootBuildFile.stage2)
                }
                // then: no compilation nor class loading
                compilationCache {
                    hits(leftBuildFile, rootBuildFile, rightBuildFile)
                }
                classLoadingCache {
                    hits(leftBuildFile, rootBuildFile, rightBuildFile)
                }
            }

            // when: other daemon
            buildForCacheInspection("--stop")
            buildForCacheInspection("help").apply {

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
        buildForCacheInspection("--stop")
        buildForCacheInspection("help", "-I", initScriptFile.absolutePath).apply {

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
            buildForCacheInspection("--stop")
            buildForCacheInspection("help").apply {

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
                static final byte[][] MEMORY_HOG = new byte[1024][1024 * 64]
                @TaskAction void runAction0() {}
            }
        """)
        val settingsFile = cachedSettingsFile(withSettings(""), false, false)
        val buildFile = cachedBuildFile(withBuildScript("""task<MyTask>("myTask")"""), true)

        // expect: memory hog released
        for (run in 1..4) {
            myTask.writeText(myTask.readText().replace("runAction${run - 1}", "runAction$run"))
            buildWithDaemonHeapSize(256, "myTask").apply {
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
    fun withMultiProjectBuild(
        settings: String = randomScriptContent(),
        root: String = randomScriptContent(),
        left: String = randomScriptContent(),
        right: String = randomScriptContent()
    ) =
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

    private
    fun randomScriptContent() =
        "println(\"${UUID.randomUUID()}\")"
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
fun ExecutionResult.classLoadingCache(action: ClassLoadingCache.() -> Unit) =
    action(ClassLoadingCache(this))


private
class ClassLoadingCache(val result: ExecutionResult) {

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


internal
fun ExecutionResult.assertOccurrenceCountOf(actionDisplayName: String, stage: CachedScript.CompilationStage, count: Int) {
    val expectedCount = if (stage.enabled) count else 0
    val logStatement = "${actionDisplayName.capitalize()} ${stage.templateId} from ${stage.source}"
    val observedCount = output.occurrenceCountOf(logStatement)
    require(observedCount == expectedCount) {
        "Expected $expectedCount but got $observedCount\n" +
            "  Looking for statement: $logStatement\n" +
            "  Build output was:\n" + output.prependIndent("    ")
    }
}


internal
fun String.occurrenceCountOf(string: String) =
    split(string).size - 1


internal
fun compilationTrace(projectRoot: File, action: CompileTrace.() -> Unit) {
    val file = File(projectRoot, "operation-trace-log.txt")
    action(CompileTrace(file.readLines()))
}


internal
class CompileTrace(private val operations: List<String>) {

    fun assertScriptCompile(stage: CachedScript.CompilationStage) {
        val description = operationDescription(stage)
        require(operations.any { it.contains(description) }) {
            "Expecting operation `$description`!"
        }
    }

    fun assertNoScriptCompile(stage: CachedScript.CompilationStage) {
        val description = operationDescription(stage)
        require(!operations.any { it.contains(description) }) {
            "Unexpected operation `$description`!"
        }
    }

    private
    fun operationDescription(stage: CachedScript.CompilationStage) =
        "Compile script ${stage.file.name} (${descriptionOf(stage)})"

    private
    fun descriptionOf(stage: CachedScript.CompilationStage) =
        if (stage.stage == "stage1") "CLASSPATH" else "BODY"
}
