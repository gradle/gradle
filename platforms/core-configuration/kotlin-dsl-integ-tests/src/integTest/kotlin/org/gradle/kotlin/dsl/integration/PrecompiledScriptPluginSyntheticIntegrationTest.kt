/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import spock.lang.Issue
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginSyntheticIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/23564")
    fun `respects offline start parameter on synthetic builds for accessors generation`() {

        file("settings.gradle.kts").appendText("""include("producer", "consumer")""")

        withKotlinDslPluginIn("producer")
        withFile(
            "producer/src/main/kotlin/offline.gradle.kts", """
            if (!gradle.startParameter.isOffline) throw IllegalStateException("Build is not offline!")
            """
        )

        withKotlinDslPluginIn("consumer").appendText(
            """
           dependencies { implementation(project(":producer")) }
            """
        )
        withFile(
            "consumer/src/main/kotlin/my-plugin.gradle.kts", """
            plugins { id("offline") }
            """
        )

        buildAndFail(":consumer:generatePrecompiledScriptPluginAccessors").apply {
            assertHasFailure("An exception occurred applying plugin request [id: 'offline']") {
                assertHasCause("Build is not offline!")
            }
        }

        build(":consumer:generatePrecompiledScriptPluginAccessors", "--offline")
    }


    @Test
    @Issue("https://github.com/gradle/gradle/issues/12955")
    fun `captures output of schema collection and displays it on errors`() {

        fun outputFrom(origin: String, logger: Boolean = true) = buildString {
            appendLine("""println("STDOUT from $origin")""")
            appendLine("""System.err.println("STDERR from $origin")""")
            if (logger) {
                appendLine("""logger.lifecycle("LIFECYCLE log from $origin")""")
                appendLine("""logger.warn("WARN log from $origin")""")
                appendLine("""logger.error("ERROR log from $origin")""")
            }
        }

        withDefaultSettingsIn("external-plugins")
        withKotlinDslPluginIn("external-plugins").appendText("""group = "test"""")
        withFile("external-plugins/src/main/kotlin/applied-output.gradle.kts", outputFrom("applied-output plugin"))
        withFile(
            "external-plugins/src/main/kotlin/applied-output-fails.gradle.kts", """
            ${outputFrom("applied-output-fails plugin")}
            TODO("applied-output-fails plugin application failure")
        """
        )

        withDefaultSettings().appendText("""includeBuild("external-plugins")""")
        withKotlinDslPlugin().appendText("""dependencies { implementation("test:external-plugins") }""")
        withPrecompiledKotlinScript(
            "some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output")
            }
        """
        )
        build(":compileKotlin").apply {
            assertNotOutput("STDOUT")
            assertNotOutput("STDERR")
            // TODO logging is not captured yet
            assertOutputContains("LIFECYCLE")
            assertOutputContains("WARN")
            assertHasErrorOutput("ERROR")
        }

        withPrecompiledKotlinScript(
            "some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output")
                TODO("some plugins block failure")
            }
        """
        )
        buildAndFail(":compileKotlin").apply {
            assertHasFailure("Execution failed for task ':generatePrecompiledScriptPluginAccessors'.") {
                assertHasCause("Failed to collect plugin requests of 'src/main/kotlin/some.gradle.kts'")
                assertHasCause("An operation is not implemented: some plugins block failure")
            }
            assertHasErrorOutput("STDOUT from plugins block")
            assertHasErrorOutput("STDERR from plugins block")
            assertNotOutput("STDOUT from applied plugin")
            assertNotOutput("STDERR from applied plugin")
        }

        withPrecompiledKotlinScript(
            "some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output-fails")
            }
        """
        )
        buildAndFail(":compileKotlin").apply {
            assertHasFailure("Execution failed for task ':generatePrecompiledScriptPluginAccessors'.") {
                assertHasCause("Failed to generate type-safe Gradle model accessors for the following precompiled script plugins")
                assertHasCause("An operation is not implemented: applied-output-fails plugin application failure")
            }
            assertHasErrorOutput("src/main/kotlin/some.gradle.kts")
            assertHasErrorOutput("STDOUT from applied-output-fails plugin")
            assertHasErrorOutput("STDERR from applied-output-fails plugin")
            assertNotOutput("STDOUT from plugins block")
            assertNotOutput("STDERR from plugins block")
            // TODO logging is not captured yet
            assertOutputContains("LIFECYCLE")
            assertOutputContains("WARN")
            assertHasErrorOutput("ERROR")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/12955")
    fun `captures output of schema collection but not of concurrent tasks`() {

        val repeatOutput = 50
        val server = BlockingHttpServer()

        try {
            server.start()

            withDefaultSettingsIn("external-plugins")
            withKotlinDslPluginIn("external-plugins").appendText("""group = "test"""")
            withFile(
                "external-plugins/src/main/kotlin/applied-output.gradle.kts", """
                println("STDOUT from applied-output plugin")
                System.err.println("STDERR from applied-output plugin")
                logger.warn("WARN from applied-output plugin")
            """
            )
            withDefaultSettings().appendText("""includeBuild("external-plugins")""")
            withKotlinDslPlugin().prependText("import java.net.URL").appendText(
                """
                dependencies { implementation("test:external-plugins") }

                abstract class ConcurrentWork : WorkAction<WorkParameters.None> {
                    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger("SOME")
                    override fun execute() {
                        URL("${server.uri("blockStart")}").readText()
                        repeat($repeatOutput) {
                            Thread.sleep(25)
                            println("STDOUT from concurrent task ${'$'}it")
                            System.err.println("STDERR from concurrent task ${'$'}it")
                            logger.warn("WARN from concurrent task ${'$'}it")
                        }
                        URL("${server.uri("blockStop")}").readText()
                    }
                }

                abstract class ConcurrentTask : DefaultTask() {
                    @get:Inject abstract val workers: WorkerExecutor
                    @TaskAction fun action() {
                        workers.noIsolation().submit(ConcurrentWork::class) {}
                    }
                }

                tasks {
                    val concurrentTask by registering(ConcurrentTask::class)
                    val generatePrecompiledScriptPluginAccessors by existing {
                        shouldRunAfter(concurrentTask)
                        doFirst {
                            URL("${server.uri("unblockStart")}").readText()
                        }
                        doLast {
                            URL("${server.uri("unblockStop")}").readText()
                        }
                    }
                }
            """
            )
            withPrecompiledKotlinScript("some.gradle.kts", """plugins { id("applied-output") }""")

            server.expectConcurrent("blockStart", "unblockStart")
            server.expectConcurrent("blockStop", "unblockStop")

            build(":concurrentTask", ":compileKotlin").apply {
                assertThat(output.lineSequence().filter { it.startsWith("STDOUT from concurrent task") }.count(), equalTo(repeatOutput))
                assertThat(error.lineSequence().filter { it.startsWith("STDERR from concurrent task") }.count(), equalTo(repeatOutput))
                assertThat(output.lineSequence().filter { it.startsWith("WARN from concurrent task") }.count(), equalTo(repeatOutput))
                assertNotOutput("STDOUT from applied-output plugin")
                assertNotOutput("STDERR from applied-output plugin")
                // TODO logging is not captured yet
                assertOutputContains("WARN from applied-output plugin")
            }
        } finally {
            server.stop()
        }
    }
}


private
fun File.prependText(text: String): File {
    writeText(text + "\n\n" + readText())
    return this
}
