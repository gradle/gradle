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

package org.gradle.kotlin.dsl.build.plugins

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.support.unzipTo

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Assert.assertThat
import org.junit.Test

import java.io.File


class KotlinDslJavaApiExtensionsPluginTest : AbstractBuildPluginTest() {

    @Test
    fun `generates compilable extensions for isolated project`() {

        withSettings("""
            rootProject.name = "some-example"
        """)

        withBuildScript("""
            import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions

            plugins {
                `java-library`
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            kotlinDslApiExtensions {
                create("main")
            }

            tasks.withType<GenerateKotlinDslApiExtensions> {
                isUseEmbeddedKotlinDslProvider.set(true)
            }

            repositories {
                jcenter()
            }
        """)

        withFile("src/main/java/some/example/Some.java", """
            package some.example;

            public interface Some {

                void rawClassTakingMethod(Class<?> clazz);
                void typedClassTakingMethod(Class<Some> clazz);
                void boundedClassTakingMethod(Class<? extends Some> clazz);

                <T> void parameterizedClassTakingMethod(Class<T> clazz);
                <T extends Some> void boundedParameterizedClassTakingMethod(Class<T> clazz);

                String groovyNamedArgumentsRawMapTakingMethod(java.util.Map args);
                String groovyNamedArgumentsUnboundedMapTakingMethod(java.util.Map<?, ?> args);
                String groovyNamedArgumentsStringMapTakingMethod(java.util.Map<String, ?> args);
                String groovyNamedArgumentsStringObjectMapTakingMethod(java.util.Map<String, Object> args);

                // TODO Add cases with more parameters and mixed class/groovy-maps
            }
        """)

        run("generateKotlinDslApiExtensions")

        existing("build/java-parameter-names").walkTopDown().single { it.isFile }.let {
            assertThat(it.name, equalTo("java-parameter-names.properties"))
            val entries = """
                some.example.Some.rawClassTakingMethod(java.lang.Class)=clazz
                some.example.Some.typedClassTakingMethod(java.lang.Class)=clazz
                some.example.Some.boundedClassTakingMethod(java.lang.Class)=clazz
                some.example.Some.parameterizedClassTakingMethod(java.lang.Class)=clazz
                some.example.Some.boundedParameterizedClassTakingMethod(java.lang.Class)=clazz
                some.example.Some.groovyNamedArgumentsRawMapTakingMethod(java.util.Map)=args
                some.example.Some.groovyNamedArgumentsUnboundedMapTakingMethod(java.util.Map)=args
                some.example.Some.groovyNamedArgumentsStringMapTakingMethod(java.util.Map)=args
                some.example.Some.groovyNamedArgumentsStringObjectMapTakingMethod(java.util.Map)=args
            """.trimIndent().lines()
            assertThat(it.readText(), allOf(entries.map { containsString(it) }))
        }

        existing("build/generated-sources").walkTopDown().single { it.isFile }.let {
            assertThat(it.name, equalTo("GeneratedSomeExampleMainKotlinDslApiExtensions.kt"))
            val extensions = listOf(
                """
                fun some.example.Some.`rawClassTakingMethod`(`clazz`: kotlin.reflect.KClass<*>): Unit =
                    `rawClassTakingMethod`(`clazz`.java)
                """,
                """
                fun some.example.Some.`typedClassTakingMethod`(`clazz`: kotlin.reflect.KClass<some.example.Some>): Unit =
                    `typedClassTakingMethod`(`clazz`.java)
                """,
                """
                fun some.example.Some.`boundedClassTakingMethod`(`clazz`: kotlin.reflect.KClass<some.example.Some>): Unit =
                    `boundedClassTakingMethod`(`clazz`.java)
                """,
                """
                fun <T : Any> some.example.Some.`parameterizedClassTakingMethod`(`clazz`: kotlin.reflect.KClass<T>): Unit =
                    `parameterizedClassTakingMethod`(`clazz`.java)
                """,
                """
                fun <T : some.example.Some> some.example.Some.`boundedParameterizedClassTakingMethod`(`clazz`: kotlin.reflect.KClass<T>): Unit =
                    `boundedParameterizedClassTakingMethod`(`clazz`.java)
                """,
                """
                fun some.example.Some.`groovyNamedArgumentsRawMapTakingMethod`(vararg `args`: Pair<String, *>): String =
                    `groovyNamedArgumentsRawMapTakingMethod`(mapOf(*`args`))
                """,
                """
                fun some.example.Some.`groovyNamedArgumentsUnboundedMapTakingMethod`(vararg `args`: Pair<String, *>): String =
                    `groovyNamedArgumentsUnboundedMapTakingMethod`(mapOf(*`args`))
                """,
                """
                fun some.example.Some.`groovyNamedArgumentsStringMapTakingMethod`(vararg `args`: Pair<String, *>): String =
                    `groovyNamedArgumentsStringMapTakingMethod`(mapOf(*`args`))
                """,
                """
                fun some.example.Some.`groovyNamedArgumentsStringObjectMapTakingMethod`(vararg `args`: Pair<String, *>): String =
                    `groovyNamedArgumentsStringObjectMapTakingMethod`(mapOf(*`args`))
                """)
            assertThat(it.readText(), allOf(extensions.map { containsMultiLineString(it) }))
        }

        run("assemble")

        existing("extract").let { extract ->
            unzipTo(extract, existing("build/libs/some-example.jar"))
            assertThat(
                extract.walkTopDown().filter { it.isFile }.map { it.relativeTo(extract).path }.toList(),
                hasItems(*listOf(
                    "some/example/Some.class",
                    "org/gradle/kotlin/dsl/GeneratedSomeExampleMainKotlinDslApiExtensions.kt",
                    "org/gradle/kotlin/dsl/GeneratedSomeExampleMainKotlinDslApiExtensionsKt.class")
                    .map { it.replace('/', File.separatorChar) }.toTypedArray()))
        }
    }

    @Test
    fun `can use resolved isolated provider runtime`() {
        withBuildScript("""
            plugins {
                `java-library`
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            kotlinDslApiExtensions {
                create("main")
            }

            repositories {
                jcenter()
            }

            configurations.all {
                resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
            }

            repositories {
                maven(url="https://repo.gradle.org/gradle/libs-snapshots-local")
                maven(url="https://repo.gradle.org/gradle/libs-releases-local")
            }
        """)

        run("generateKotlinDslApiExtensions")
    }
}
