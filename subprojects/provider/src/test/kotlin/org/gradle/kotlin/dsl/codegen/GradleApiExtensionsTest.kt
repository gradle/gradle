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

import java.io.File
import java.util.function.Consumer

import kotlin.reflect.KClass


class GradleApiExtensionsTest : TestWithClassPath() {

    @Test
    fun `maps java-lang-Class to kotlin-reflect-KClass`() {

        apiKotlinExtensionsGenerationFor(ClassToKClass::class) {

            assertGeneratedExtensions(
                """
                inline fun <T : Any> org.gradle.kotlin.dsl.codegen.ClassToKClass.`methodParameterizedClass`(`type`: kotlin.reflect.KClass<T>): Unit =
                    `methodParameterizedClass`(`type`.java)
                """
            )

            assertUsageCompilation(
                """
                import kotlin.reflect.*

                fun classToKClass(subject: ClassToKClass) {

                    subject.rawClass(type = String::class)
                    subject.unknownClass(type = String::class)
                    subject.invariantClass(type = Number::class)
                    subject.covariantClass(type = Int::class)
                    subject.contravariantClass(type = Number::class)

                    subject.varargOfClasses(Number::class, Int::class)
                    subject.arrayOfClasses(types = arrayOf(Number::class, Int::class))
                    subject.collectionOfClasses(listOf(Number::class, Int::class))

                    subject.methodParameterizedClass(type = Int::class)
                    subject.covariantMethodParameterizedClass(type = Int::class)
                    subject.methodParameterizedCovariantClass(type = Int::class)
                    subject.methodParameterizedContravariantClass(type = Int::class)
                    subject.covariantMethodParameterizedCovariantClass(type = Int::class)
                    subject.covariantMethodParameterizedContravariantClass(type = Int::class)
                }
                """
            )
        }
    }

    @Test
    fun `maps Groovy named arguments to Kotlin vararg of Pair`() {

        apiKotlinExtensionsGenerationFor(GroovyNamedArguments::class, Consumer::class) {

            assertGeneratedExtensions(
                """
                inline fun org.gradle.kotlin.dsl.codegen.GroovyNamedArguments.`rawMap`(vararg `args`: Pair<String, *>): Unit =
                    `rawMap`(mapOf(*`args`))
                """,
                """
                inline fun org.gradle.kotlin.dsl.codegen.GroovyNamedArguments.`stringUnknownMap`(vararg `args`: Pair<String, *>): Unit =
                    `stringUnknownMap`(mapOf(*`args`))
                """,
                """
                inline fun org.gradle.kotlin.dsl.codegen.GroovyNamedArguments.`stringObjectMap`(vararg `args`: Pair<String, *>): Unit =
                    `stringObjectMap`(mapOf(*`args`))
                """,
                """
                inline fun org.gradle.kotlin.dsl.codegen.GroovyNamedArguments.`mapWithOtherParameters`(`foo`: String, `bar`: Int, vararg `args`: Pair<String, *>): Unit =
                    `mapWithOtherParameters`(mapOf(*`args`), `foo`, `bar`)
                """,
                """
                inline fun org.gradle.kotlin.dsl.codegen.GroovyNamedArguments.`mapWithLastSamAndOtherParameters`(`foo`: String, vararg `args`: Pair<String, *>, `bar`: java.util.function.Consumer<String>): Unit =
                    `mapWithLastSamAndOtherParameters`(mapOf(*`args`), `foo`, `bar`)
                """
            )

            assertUsageCompilation()
        }
    }

    private
    fun apiKotlinExtensionsGenerationFor(vararg classes: KClass<*>, action: ApiKotlinExtensionsGeneration.() -> Unit) =
        ApiKotlinExtensionsGeneration(jarClassPathWith(*classes).asFiles).apply(action)

    private
    data class ApiKotlinExtensionsGeneration(val apiJars: List<File>) {
        lateinit var generatedSourceFiles: List<File>
    }

    private
    fun ApiKotlinExtensionsGeneration.assertGeneratedExtensions(vararg expectedExtensions: String) {

        generatedSourceFiles = generateKotlinDslApiExtensionsSourceTo(
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

        println(generatedSourceCode)

        expectedExtensions.forEach { expectedExtension ->
            assertThat(generatedSourceCode, containsMultiLineString(expectedExtension))
        }
    }

    private
    fun ApiKotlinExtensionsGeneration.assertUsageCompilation(vararg extensionsUsages: String) {

        val useDir = file("use").also { it.mkdirs() }
        val usageFiles = extensionsUsages.mapIndexed { idx, usage ->
            useDir.resolve("usage$idx.kt").also {
                it.writeText("""
                import org.gradle.kotlin.dsl.codegen.*
                import org.gradle.kotlin.dsl.*

                $usage
                """.trimIndent())
            }
        }

        StandardKotlinFileCompiler.compileToDirectory(
            file("out").also { it.mkdirs() },
            generatedSourceFiles + usageFiles,
            apiJars
        )
    }
}


private
val fixtureParameterNamesSupplier = { key: String ->
    when {
        key.startsWith("${ClassToKClass::class.qualifiedName}.") -> when {
            key.contains("Class(") -> listOf("type")
            key.contains("Classes(") -> listOf("types")
            else -> null
        }
        key.startsWith("${GroovyNamedArguments::class.qualifiedName}.") -> when {
            key.contains("Map(") -> listOf("args")
            key.contains("Parameters(") -> listOf("args", "foo", "bar")
            else -> null
        }
        else -> null
    }
}
