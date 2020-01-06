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

import com.nhaarman.mockito_kotlin.mock

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.fixtures.withClassLoaderFor
import org.gradle.kotlin.dsl.support.compileKotlinScriptToDirectory
import org.gradle.kotlin.dsl.support.messageCollectorFor
import org.gradle.kotlin.dsl.support.scriptDefinitionFromTemplate

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

import org.junit.Test

import java.io.File


class KotlinScriptCompilerTest : TestWithTempFiles() {

    @Test
    fun canInjectImplicitReceiver() {
        outputDir().let { outputDir ->

            compileKotlinScriptTo(
                outputDir,
                "bar()",
                scriptDefinitionFromTemplate(
                    template = FooTemplate::class,
                    implicitImports = emptyList(),
                    implicitReceiver = FooReceiver::class
                )
            )

            withClassLoaderFor(outputDir) {

                val host = mock<FooHost>()
                val receiver = FooReceiver()

                loadClass("Script")
                    .getDeclaredConstructor(FooHost::class.java, FooReceiver::class.java)
                    .newInstance(host, receiver)

                assertThat(
                    receiver.bars,
                    equalTo(1)
                )
            }
        }
    }

    open class FooTemplate(
        @Suppress("unused_parameter") host: FooHost
    )

    interface FooHost

    class FooReceiver {
        var bars = 0
        fun bar() {
            bars += 1
        }
    }

    private
    fun outputDir() = root.resolve("classes").apply { mkdir() }

    private
    fun compileKotlinScriptTo(
        outputDir: File,
        script: String,
        scriptDefinition: ScriptDefinition
    ) {
        compileKotlinScriptToDirectory(
            outputDir,
            file("script.kts").apply {
                writeText(script)
            },
            scriptDefinition,
            testRuntimeClassPath.asFiles,
            messageCollectorFor(mock())
        )
    }
}
