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
import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.allOf
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

            dependencies {
                compileOnly(gradleApi())
            }

            kotlinDslApiExtensions {
                create("main") {
                    packageName.set("org.gradle.kotlin.gradle.ext")
                }
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

            import org.gradle.api.Action;

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

                void mapClassTakingMethod(java.util.Map args, Class<Some> clazz);
                void mapClassActionTakingMethod(java.util.Map args, Class<Some> clazz, Action<Some> action);
            }
        """)

        withFile("src/main/java/some/example/Generics.java", """
            package some.example;

            import org.gradle.api.Action;
            import org.gradle.api.Incubating;
            import org.gradle.api.specs.Spec;

            public interface Generics<T extends Some> {

                void rawClassTakingMethod(Class<?> clazz);
                void typedClassTakingMethod(Class<T> clazz);
                void boundedClassTakingMethod(Class<? extends T> clazz);

                @Deprecated
                Generics<T> matching(Class<Spec<T>> specType);

                @Deprecated
                @Incubating
                <S extends T> Generics<S> withType(Class<S> type, Action<S> action);

                @Incubating
                <S extends T> Generics<S> withType(java.util.Map<String, ?> properties, Class<S> type, Action<S> action);
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
                some.example.Some.mapClassTakingMethod(java.util.Map,java.lang.Class)=args,clazz
                some.example.Some.mapClassActionTakingMethod(java.util.Map,java.lang.Class,org.gradle.api.Action)=args,clazz,action
                some.example.Generics.rawClassTakingMethod(java.lang.Class)=clazz
                some.example.Generics.typedClassTakingMethod(java.lang.Class)=clazz
                some.example.Generics.boundedClassTakingMethod(java.lang.Class)=clazz
                some.example.Generics.matching(java.lang.Class)=specType
                some.example.Generics.withType(java.lang.Class,org.gradle.api.Action)=type,action
                some.example.Generics.withType(java.util.Map,java.lang.Class,org.gradle.api.Action)=properties,type,action
            """.trimIndent().lines()
            assertThat(it.readLines().sorted(), equalTo(entries.sorted()))
        }

        existing("build/generated-sources").walkTopDown().filter { it.isFile }.toList().let { sourceFiles ->

            assertThat(
                sourceFiles.map { it.name },
                hasItems("GeneratedSomeExampleMainKotlinDslApiExtensions0.kt", "GeneratedSomeExampleMainKotlinDslApiExtensions1.kt"))

            val extensions = listOf(
                "package org.gradle.kotlin.gradle.ext",
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
                """,
                """
                fun some.example.Some.`mapClassTakingMethod`(`clazz`: kotlin.reflect.KClass<some.example.Some>, vararg `args`: Pair<String, *>): Unit =
                    `mapClassTakingMethod`(mapOf(*`args`), `clazz`.java)
                """,
                """
                fun some.example.Some.`mapClassActionTakingMethod`(`clazz`: kotlin.reflect.KClass<some.example.Some>, vararg `args`: Pair<String, *>, `action`: some.example.Some.() -> Unit): Unit =
                    `mapClassActionTakingMethod`(mapOf(*`args`), `clazz`.java, `action`)
                """,
                """
                fun <T : some.example.Some> some.example.Generics<T>.`rawClassTakingMethod`(`clazz`: kotlin.reflect.KClass<*>): Unit =
                    `rawClassTakingMethod`(`clazz`.java)
                """,
                """
                @Deprecated("Deprecated Gradle API")
                fun <T : some.example.Some> some.example.Generics<T>.`matching`(`specType`: kotlin.reflect.KClass<org.gradle.api.specs.Spec<T>>): some.example.Generics<T> =
                    `matching`(`specType`.java)
                """,
                """
                @Deprecated("Deprecated Gradle API")
                @org.gradle.api.Incubating
                fun <S : T, T : some.example.Some> some.example.Generics<T>.`withType`(`type`: kotlin.reflect.KClass<S>, `action`: S.() -> Unit): some.example.Generics<S> =
                    `withType`(`type`.java, `action`)
                """,
                """
                @org.gradle.api.Incubating
                fun <S : T, T : some.example.Some> some.example.Generics<T>.`withType`(`type`: kotlin.reflect.KClass<S>, vararg `properties`: Pair<String, *>, `action`: S.() -> Unit): some.example.Generics<S> =
                    `withType`(mapOf(*`properties`), `type`.java, `action`)
                """)

            assertThat(
                sourceFiles.joinToString("\n") { it.readText() },
                allOf(extensions.map { containsMultiLineString(it) }))
        }

        run("assemble")

        existing("extract").let { extract ->
            unzipTo(extract, existing("build/libs/some-example.jar"))
            assertThat(
                extract.walkTopDown().filter { it.isFile }.map { it.relativeTo(extract).path }.toList(),
                hasItems(*listOf(
                    "some/example/Some.class",
                    "org/gradle/kotlin/gradle/ext/GeneratedSomeExampleMainKotlinDslApiExtensions0Kt.class",
                    "org/gradle/kotlin/gradle/ext/GeneratedSomeExampleMainKotlinDslApiExtensions1Kt.class")
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

    @Test
    fun `can be applied to project without java sources`() {
        withBuildScript("""
            import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions

            plugins {
                `java-library`
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            kotlinDslApiExtensions {
                create("main")
            }

            java.sourceSets["main"].java.setSrcDirs(emptyList<Any>())

            tasks.withType<GenerateKotlinDslApiExtensions> {
                isUseEmbeddedKotlinDslProvider.set(true)
            }

            repositories {
                jcenter()
            }
        """)

        run("build").apply {
            assertThat(task(":generateJavaParameterNamesIndex")?.outcome, equalTo(TaskOutcome.SUCCESS))
            assertThat(task(":generateKotlinDslApiExtensions")?.outcome, equalTo(TaskOutcome.SUCCESS))
        }
    }

    @Test
    fun `parameter names generation dependencies`() {
        withSettings("""
            include("left", "right")
        """)

        withBuildScriptIn("left", """
            plugins { java }
        """)

        withBuildScriptIn("right", """
            import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions

            plugins {
                java
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            dependencies {
                implementation(project(":left"))
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

            tasks.register("generateSomeCode") {
                val outputDir = layout.buildDirectory.dir("generated")
                java.sourceSets["main"].java.srcDir(files(outputDir).apply { builtBy(this@register) })
                doLast {
                    outputDir.get().asFile.resolve("Generated.java").run {
                        parentFile.mkdirs()
                        writeText("class Generated {}")
                    }
                }
            }
        """)

        run(":right:generateJavaParameterNamesIndex").apply {
            assertThat(task(":left:compileJava")?.outcome, equalTo(TaskOutcome.NO_SOURCE))
            assertThat(task(":right:generateSomeCode")?.outcome, equalTo(TaskOutcome.SUCCESS))
            assertThat(task(":right:generateJavaParameterNamesIndex")?.outcome, equalTo(TaskOutcome.SUCCESS))
        }
    }
}
