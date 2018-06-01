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

import org.gradle.kotlin.dsl.fixtures.customInstallation
import org.gradle.kotlin.dsl.support.unzipTo

import org.junit.Test
import java.io.File
import java.io.FileFilter


class KotlinDslJavaApiExtensionsPluginTest : AbstractBuildPluginTest() {

    @Test
    fun `generates extensions for isolated project`() {

        withSettings("""
            rootProject.name = "core-api"
        """)

        withBuildScript("""
            import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions

            plugins {
                `java-library`
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            repositories {
                jcenter()
            }

            $gradlePublicApiObjectDeclaration

            kotlinDslApiExtensions {
                create("main") {
                    // includes.set(PublicApi.includes)
                    excludes.set(PublicApi.excludes)
                }
            }

            tasks.withType<GenerateKotlinDslApiExtensions> {
                isUseEmbeddedKotlinDslProvider.set(true)
            }

            configurations.all {
                resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
            }

            repositories {
                maven(url="https://repo.gradle.org/gradle/libs-snapshots-local")
                maven(url="https://repo.gradle.org/gradle/libs-releases-local")
            }
        """)

        withFile("src/main/java/com/example/Some.java", """
            package com.example;

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
            }
        """)

        println()
        run("generateKotlinDslApiExtensions").apply {
            println(output)
        }

        println("\n>> Java parameter names indices")
        existing("build/java-parameter-names").walkTopDown().filter { it.isFile }.forEach {
            println(it)
            println(it.readText().prependIndent())
        }

        println("\n>> Generated sources")
        existing("build/generated-sources").walkTopDown().filter { it.isFile }.forEach {
            println(it)
            println(it.readText().prependIndent())
        }

        println()
        run("assemble").apply {
            println(output)
        }

        println("\n>> Classes")
        existing("build/classes").walkTopDown().filter { it.isFile }.forEach { println(it) }

        println("\n>> Libs")
        existing("build/libs").walkTopDown().filter { it.isFile }.forEach { println(it) }

        println("\n>> Jar content")
        existing("extract").let { extract ->
            unzipTo(extract, existing("build/libs/core-api.jar"))
            extract.walkTopDown().filter { it.isFile }.forEach { println(it) }
        }

        // TODO add assertions
    }

    @Test
    fun `generates extensions for the whole gradle public api`() {
        // TODO implement me!
    }
}


private
val gradleClasspath: List<File> by lazy {
    customInstallation().let { custom ->
        sequenceOf(custom.resolve("lib"), custom.resolve("lib/plugins")).flatMap {
            it.listFiles(FileFilter { it.name.endsWith(".jar") }).asSequence()
        }.toList()
    }
}


private
val gradleClasspathString: String by lazy {
    gradleClasspath.map { it.absolutePath }.joinToString(", ") { "\"$it\"" }
}


// See https://github.com/gradle/gradle/blob/master/buildSrc/subprojects/configuration/src/main/kotlin/org/gradle/gradlebuild/public-api.kt
private
val gradlePublicApiObjectDeclaration = """
object PublicApi {
    val includes = listOf("org/gradle/*",
        "org/gradle/api/**",
        "org/gradle/authentication/**",
        "org/gradle/buildinit/**",
        "org/gradle/caching/**",
        "org/gradle/concurrent/**",
        "org/gradle/deployment/**",
        "org/gradle/external/javadoc/**",
        "org/gradle/ide/**",
        "org/gradle/includedbuild/**",
        "org/gradle/ivy/**",
        "org/gradle/jvm/**",
        "org/gradle/language/**",
        "org/gradle/maven/**",
        "org/gradle/nativeplatform/**",
        "org/gradle/normalization/**",
        "org/gradle/platform/**",
        "org/gradle/play/**",
        "org/gradle/plugin/devel/**",
        "org/gradle/plugin/repository/*",
        "org/gradle/plugin/use/*",
        "org/gradle/plugin/management/*",
        "org/gradle/plugins/**",
        "org/gradle/process/**",
        "org/gradle/testfixtures/**",
        "org/gradle/testing/jacoco/**",
        "org/gradle/tooling/**",
        "org/gradle/swiftpm/**",
        "org/gradle/model/**",
        "org/gradle/testkit/**",
        "org/gradle/testing/**",
        "org/gradle/vcs/**",
        "org/gradle/workers/**")

    val excludes = listOf("**/internal/**")
}
""".trimIndent()
