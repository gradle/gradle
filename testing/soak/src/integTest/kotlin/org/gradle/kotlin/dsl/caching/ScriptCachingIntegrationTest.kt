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

import org.assertj.core.api.Assertions.assertThat
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.kotlin.dsl.caching.fixtures.CachedScript
import org.gradle.kotlin.dsl.caching.fixtures.KotlinDslCacheFixture
import org.gradle.kotlin.dsl.caching.fixtures.cachedBuildFile
import org.gradle.kotlin.dsl.caching.fixtures.cachedGradleScript
import org.gradle.kotlin.dsl.caching.fixtures.cachedInitializationFile
import org.gradle.kotlin.dsl.caching.fixtures.cachedProjectScript
import org.gradle.kotlin.dsl.caching.fixtures.cachedSettingsFile
import org.gradle.kotlin.dsl.caching.fixtures.cachedSettingsScript
import org.gradle.kotlin.dsl.caching.fixtures.classLoadingCache
import org.gradle.kotlin.dsl.caching.fixtures.compilationCache
import org.gradle.kotlin.dsl.caching.fixtures.compilationTrace
import org.gradle.kotlin.dsl.execution.Program
import org.gradle.kotlin.dsl.execution.ProgramKind.TopLevel
import org.gradle.kotlin.dsl.execution.ProgramParser
import org.gradle.kotlin.dsl.execution.ProgramSource
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.junit.Test
import java.util.UUID


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
        val memoryHogMb = 128
        val myTask = withFile(
            "buildSrc/src/main/groovy/MyTask.groovy",
            """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                static final byte[][] MEMORY_HOG = new byte[1024][1024 * $memoryHogMb]
                @TaskAction void runAction0() {}
            }
            """
        )
        val settingsFile = cachedSettingsFile(withSettings(""), false, false)
        val buildFile = cachedBuildFile(withBuildScript("""task<MyTask>("myTask")"""), true)

        // and: kotlin-dsl cache assertions
        fun KotlinDslCacheFixture.assertCacheHits(run: Int) {
            if (run == 1) {
                misses(settingsFile.stage1)
                hits(settingsFile.stage2)
            } else {
                // buildSrc isn't in the settings classpath
                hits(settingsFile)
            }
            misses(buildFile)
        }

        // expect: memory hog released
        val runs = 10
        // For some reason we have 5 references to the task class.
        val daemonHeapMb = memoryHogMb * 5 + 400
        for (run in 1..runs) {
            println("Run number $run")
            myTask.writeText(myTask.readText().replace("runAction${run - 1}", "runAction$run"))
            buildWithDaemonHeapSize(daemonHeapMb, "myTask").apply {
                compilationCache {
                    assertCacheHits(run)
                }
                classLoadingCache {
                    assertCacheHits(run)
                }
            }
        }
        val daemonFixture = DaemonLogsAnalyzer.newAnalyzer(executer.daemonBaseDir)
        assertThat(daemonFixture.daemons).hasSize(1)
    }

    @Test
    fun `writes build cache entries`() {
        val result = expectCacheEntriesWritten(5, false)
        result.assertOutputContains("Stored cache entry for Kotlin DSL version catalog plugin accessors")
        result.assertOutputContains("Stored cache entry for Kotlin DSL plugin specs accessors")
        result.assertOutputContains("Stored cache entry for Kotlin DSL accessors")
        result.assertOutputContains("Stored cache entry for Kotlin DSL script compilation (Project/TopLevel/stage1)")
        result.assertOutputContains("Stored cache entry for Kotlin DSL script compilation (Project/TopLevel/stage2)")
    }

    @Test
    fun `writes no build cache entries when script caching disabled`() {
        val result = expectCacheEntriesWritten(3, true)
        result.assertOutputContains("Stored cache entry for Kotlin DSL version catalog plugin accessors")
        result.assertOutputContains("Stored cache entry for Kotlin DSL plugin specs accessors")
        result.assertOutputContains("Stored cache entry for Kotlin DSL accessors")
        result.assertNotOutput("Stored cache entry for Kotlin DSL script compilation")
    }

    private
    fun expectCacheEntriesWritten(expectedEntryCount: Int, scriptCachingDisabled: Boolean): ExecutionResult {
        return withOwnGradleUserHomeDir("verifying local build cache content") {
            withSettings(
                """
                    enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
                    ${randomScriptContent()}
                """.trimIndent()
            )
            withBuildScriptIn(".", randomScriptContent())
            withFolders {
                "gradle" {
                    withFile(
                        "libs.versions.toml",
                        """
                            [versions]
                            groovy = "3.0.5"
                            checkstyle = "8.37"

                            [libraries]
                            groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
                            groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
                        """.trimIndent()
                    )
                }
            }

            val result = buildForCacheInspection("help", "--build-cache", "--info", "-Dorg.gradle.internal.kotlin-script-caching-disabled=$scriptCachingDisabled")

            if (scriptCachingDisabled) {
                result.assertNotOutput("Stored cache entry for Kotlin DSL script compilation")
                result.assertOutputContains("Caching of Kotlin script compilation disabled by property")
            } else {
                result.assertOutputContains("Stored cache entry for Kotlin DSL script compilation")
                result.assertNotOutput("Caching of Kotlin script compilation disabled by property")
            }

            val localBuildCacheDir = executer.gradleUserHomeDir.resolve("caches/build-cache-1")
            val localBuildCacheFiles = localBuildCacheDir.list { _, fileName -> fileName != "gc.properties" && fileName != "build-cache-1.lock" }

            assertThat(localBuildCacheFiles).hasSize(expectedEntryCount)
            result
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
                withSettings(
                    """
                    $settings
                    rootProject.name = "${projectRoot.name}" // distinguish settings files
                    include("right", "left")
                    """
                ),
                settings.contains("buildscript {"),
                true
            ),
            cachedBuildFile(
                withBuildScript(root),
                hasBody(root)
            ),
            cachedBuildFile(
                withBuildScriptIn("left", left),
                hasBody(left)
            ),
            cachedBuildFile(
                withBuildScriptIn("right", right),
                hasBody(right)
            )
        )

    private
    fun randomScriptContent() =
        "println(\"${UUID.randomUUID()}\")"
}


private
fun hasBody(script: String) =
    ProgramParser.parse(ProgramSource("script.gradle.kts", script), TopLevel, ProgramTarget.Project).document.let {
        it is Program.Script || it is Program.Staged
    }


private
data class MultiProjectCachedScripts(
    val settingsFile: CachedScript.WholeFile,
    val rootBuildFile: CachedScript.WholeFile,
    val leftBuildFile: CachedScript.WholeFile,
    val rightBuildFile: CachedScript.WholeFile
)
