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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest
import org.gradle.kotlin.dsl.fixtures.classLoaderFor

import org.junit.Before


open class AbstractPrecompiledScriptPluginTest : AbstractPluginTest() {

    @Before
    fun setupPluginTest() {
        requireGradleDistributionOnEmbeddedExecuter()
    }

    protected
    fun givenPrecompiledKotlinScript(fileName: String, code: String) {
        withKotlinDslPlugin()
        withPrecompiledKotlinScript(fileName, code)
        compileKotlin()
    }

    protected
    fun withPrecompiledKotlinScript(fileName: String, code: String) {
        withFile("src/main/kotlin/$fileName", code)
    }

    protected
    inline fun <reified T> instantiatePrecompiledScriptOf(target: T, className: String): Any =
        loadCompiledKotlinClass(className)
            .getConstructor(T::class.java)
            .newInstance(target)

    protected
    fun loadCompiledKotlinClass(className: String): Class<*> =
        classLoaderFor(existing("build/classes/kotlin/main"))
            .loadClass(className)

    protected
    fun withKotlinDslPlugin() =
        withKotlinDslPluginIn(".")

    protected
    fun withKotlinDslPluginIn(baseDir: String) =
        withBuildScriptIn(baseDir, scriptWithKotlinDslPlugin())

    protected
    fun scriptWithKotlinDslPlugin(): String =
        """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """

    protected
    fun compileKotlin(taskName: String = "classes") {
        buildWithPlugin(taskName).assertTaskExecuted(":compileKotlin")
    }
}
