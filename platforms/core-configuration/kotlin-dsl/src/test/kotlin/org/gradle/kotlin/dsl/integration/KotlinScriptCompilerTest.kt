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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.fixtures.withClassLoaderFor
import org.gradle.kotlin.dsl.integration.KotlinScriptCompilerTest.TheImplicitReceiver
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.btaCompileKotlinScriptToDirectory
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.kotlin.buildtools.api.arguments.ExperimentalCompilerArgument
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers

object TheScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        implicitReceivers(TheImplicitReceiver::class)
        defaultImports(emptyList())
    })

@KotlinScript(compilationConfiguration = TheScriptCompilationConfiguration::class)
open class TheKotlinScriptTemplate(
    @Suppress("unused_parameter") host: Host
) {
    interface Host
}


class KotlinScriptCompilerTest : TestWithTempFiles() {

    @Test
    fun canInjectImplicitReceiver() {
        outputDir().let { outputDir ->

            val script = """
                    println("Calling bar()...") // making sure we have access to the standard library
                    bar()
                    """.trimIndent()

            compileKotlinScriptTo(outputDir, script, TheKotlinScriptTemplate::class)

            withClassLoaderFor(outputDir) {

                val host = mock<TheKotlinScriptTemplate.Host>()
                val receiver = TheImplicitReceiver()

                loadClass("Script")
                    .getDeclaredConstructor(TheKotlinScriptTemplate.Host::class.java, TheImplicitReceiver::class.java)
                    .newInstance(host, receiver)

                assertThat(
                    receiver.bars,
                    equalTo(1)
                )
            }
        }
    }

    class TheImplicitReceiver {
        var bars = 0
        fun bar() {
            bars += 1
        }
    }

    private
    fun outputDir() = root.resolve("classes").apply { mkdir() }

    @OptIn(ExperimentalCompilerArgument::class)
    private
    fun compileKotlinScriptTo(
        outputDir: File,
        script: String,
        template: KClass<out Any>,
    ) {
        btaCompileKotlinScriptToDirectory(
            outputDir,
            KotlinCompilerOptions(),
            file("script.kts").apply {
                writeText(script)
            },
            testRuntimeClassPath.asFiles,
            template,
            mock()
        )
    }
}
