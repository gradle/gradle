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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.classLoaderFor

import org.junit.Before


open class AbstractPrecompiledScriptPluginTest : AbstractKotlinIntegrationTest() {

    @Before
    fun setupPluginTest() {
        executer.beforeExecute {
            // Ignore stacktraces when the Kotlin daemon fails
            // See https://github.com/gradle/gradle-private/issues/2936
            it.withStackTraceChecksDisabled()
        }
    }

    protected
    inline fun <reified T> instantiatePrecompiledScriptOf(target: T, className: String): Any =
        loadCompiledKotlinClass(className)
            .getConstructor(T::class.java, T::class.java)
            .newInstance(target, target)

    protected
    fun loadCompiledKotlinClass(className: String): Class<*> =
        classLoaderFor(existing("build/classes/kotlin/main"))
            .loadClass(className)
}
