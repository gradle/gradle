/*
 * Copyright 2026 the original author or authors.
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
@file:Suppress("DEPRECATION")

package org.gradle.kotlin.dsl.precompile

import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptHashing
import org.gradle.test.fixtures.ExpectDeprecationExtension
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import kotlin.script.dependencies.ScriptContents


class PrecompiledScriptDependenciesResolverTest {

    private
    val environment = mapOf(
        kotlinDslImplicitImports to "a.*:b.*",
        KotlinScriptHashing.precompiledScriptKeyOfText("package my.conv\n", "my-plugin.gradle.kts") to "gradle.kotlin.dsl.accessors._abc.*",
    )

    @Test
    fun `resolves accessors imports by package and file name`() {
        // The deprecation is reported once, when the class is first used
        ExpectDeprecationExtension.intercept("The org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver type has been deprecated.") {
            assertThat(
                importsOf(File("src/main/kotlin/my/conv/my-plugin.gradle.kts"), "package my.conv\n\nplugins { java }\n"),
                equalTo(listOf("a.*", "b.*", "gradle.kotlin.dsl.accessors._abc.*"))
            )

            // Without a file name, only the shared imports are found
            assertThat(importsOf(null, "package my.conv\n"), equalTo(listOf("a.*", "b.*")))
            assertThat(
                PrecompiledScriptDependenciesResolver.implicitImportsForScript("package my.conv\n", environment),
                equalTo(listOf("a.*", "b.*"))
            )
        }
    }

    private
    fun importsOf(scriptFile: File?, scriptText: String): List<String> =
        PrecompiledScriptDependenciesResolver().resolve(
            object : ScriptContents {
                override val file = scriptFile
                override val annotations = emptyList<Annotation>()
                override val text = scriptText
            },
            environment,
            { _, _, _ -> },
            null
        ).get()!!.imports.toList()
}
