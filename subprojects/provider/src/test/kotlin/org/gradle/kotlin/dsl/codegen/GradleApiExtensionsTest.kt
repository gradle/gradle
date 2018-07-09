/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.codegen

import org.gradle.kotlin.dsl.accessors.TestWithClassPath

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.junit.Assert.assertThat
import org.junit.Test


class GradleApiExtensionsTest : TestWithClassPath() {

    @Test
    fun `maps java-lang-Class to kotlin-reflect-KClass`() {

        val apiJars = jarClassPathWith(
            ClassToKClass::class,
            ClassToKClassParameterizedType::class
        ).asFiles

        val generatedSourceFiles = generateKotlinDslApiExtensionsSourceTo(
            file("src").also { it.mkdirs() },
            "org.gradle.kotlin.dsl",
            "SourceBaseName",
            apiJars,
            emptyList(),
            emptyList(),
            emptyList(),
            fixtureParameterNamesSupplier
        )

        val generatedSourceCode = generatedSourceFiles.joinToString("") {
            it.readText().substringAfter("package org.gradle.kotlin.dsl\n\n")
        }

        listOf(
            """
            inline fun <T : Any> org.gradle.kotlin.dsl.codegen.ClassToKClass.`methodParameterizedClass`(`type`: kotlin.reflect.KClass<T>): Unit =
                `methodParameterizedClass`(`type`.java)
            """
        ).forEach { expectedExtension ->
            assertThat(generatedSourceCode, containsMultiLineString(expectedExtension))
        }

        val usageFiles = listOf(
            file("use/usage.kt").also {
                it.parentFile.mkdirs()
                it.writeText("""
                import org.gradle.kotlin.dsl.codegen.*
                import org.gradle.kotlin.dsl.*

                import kotlin.reflect.*

                fun classToKClass(subject: ClassToKClass) {

                    subject.rawClass(type = String::class)
                    subject.unboundedClass(type = String::class)

                    subject.noBoundClass(type = Number::class)
                    subject.upperBoundClass(type = Int::class)
                    subject.lowerBoundClass(type = Number::class)

                    subject.methodParameterizedClass(type = Int::class)
                    subject.boundedMethodParameterizedClass(type = Int::class)
                }
                """.trimIndent())
            }
        )

        StandardKotlinFileCompiler.compileToDirectory(
            file("out").also { it.mkdirs() },
            generatedSourceFiles + usageFiles,
            apiJars
        )
    }
}


private
val fixtureParameterNamesSupplier = { key: String ->
    if (key.startsWith("${ClassToKClass::class.qualifiedName!!}.")) {
        when {
            key.contains("Class(") -> listOf("type")
            key.contains("Classes(") -> listOf("types")
            else -> null
        }
    } else null
}
