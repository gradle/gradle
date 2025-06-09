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

package org.gradle.kotlin.dsl.compile

import org.junit.Test


class BuildSrcScriptCompileAvoidanceIntegrationTest : AbstractCompileAvoidanceIntegrationTest() {

    @Test
    fun `avoids buildscript recompilation on public function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("foo")
            }
            """
        )
        withUniqueScript("$packageName.foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            fun foo() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript on inline function change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("foo")
            }
            """
        )
        withUniqueScript("$packageName.foo()")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "Foo",
            """
            inline fun foo() {
                println("bar")
            }
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript on const val field change in buildSrc script`() {
        val packageName = givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "foo"
            """
        )
        withUniqueScript("println($packageName.FOO)")
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        givenKotlinScriptInBuildSrcContains(
            "foo",
            """
            const val FOO = "bar"
            """
        )
        configureProject().assertBuildScriptBodyRecompiled().assertOutputContains("bar")
    }
}
