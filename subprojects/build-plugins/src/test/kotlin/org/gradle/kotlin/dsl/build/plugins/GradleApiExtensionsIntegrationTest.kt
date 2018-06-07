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
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.support.unzipTo

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Assert.assertThat
import org.junit.Test

import java.io.File


/**
 * Assert that compilable Kotlin extensions are generated for a subset of the Gradle API.
 *
 * This test approximate the Gradle API content by using jars from the current installation
 * and sources from the matching `src` distribution.
 *
 * It excludes sources of subprojects that depend on external tools fetched on demand.
 * Those are not included in the current installation.
 */
class GradleApiExtensionsIntegrationTest : AbstractBuildPluginTest() {

    @Test
    fun `generates compilable extensions for the Gradle API`() {

        withSettings("""
            rootProject.name = "gradle-api-extensions"
        """)

        withBuildScript("""
            import org.gradle.internal.installation.CurrentGradleInstallation
            import org.gradle.kotlin.dsl.build.tasks.GenerateKotlinDslApiExtensions

            plugins {
                `java-library`
                id("org.gradle.kotlin.dsl.build.java-api-extensions")
            }

            $gradlePublicApiObjectDeclaration

            // Get subset of Gradle API sources from a source distro
            // Exclude Groovy sources and sources depending on external tools fetched on-demand
            val sourceDirs = ${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().filterNot { dir ->
                dir.name.endsWith("resources")
                    || dir.name.endsWith("groovy")
                    || listOf(
                        "subprojects/internal",
                        "subprojects/antlr",
                        "subprojects/build-init",
                        "subprojects/code-quality",
                        "subprojects/ide",
                        "subprojects/language-scala",
                        "subprojects/platform-play",
                        "subprojects/scala")
                    .map { it.replace('/', File.separatorChar) }.any { dir.path.contains(it) }
            }

            // Get dependencies jars from current gradle installation
            val gradleApiDependencies = CurrentGradleInstallation.get()!!.libDirs.flatMap {
                it.listFiles().filter { it.name.endsWith(".jar") && !it.name.startsWith("gradle-") }
            }

            // Register sources and dependencies
            java.sourceSets["main"].java.srcDirs(*sourceDirs.toTypedArray())
            dependencies {
                compileOnly(files(gradleApiDependencies))
            }

            kotlinDslApiExtensions {
                create("main") {
                    includes.set(PublicApi.includes)
                    excludes.set(PublicApi.excludes)
                }
            }

            tasks.withType<GenerateKotlinDslApiExtensions> {
                isUseEmbeddedKotlinDslProvider.set(true)
            }

            tasks.withType<JavaCompile> {
                options.isFork = true
            }

            repositories {
                jcenter()
            }
        """)

        run("generateKotlinDslApiExtensions")

        existing("build/generated-sources").walkTopDown().single { it.isFile }.let {
            assertThat(it.name, equalTo("GeneratedGradleApiExtensionsMainKotlinDslApiExtensions.kt"))
            val extensions = listOf(
                """
                fun <S : T, T : Any> org.gradle.api.DomainObjectSet<T>.`withType`(`type`: kotlin.reflect.KClass<S>): org.gradle.api.DomainObjectSet<S> =
                    `withType`(`type`.java)
                """,
                """
                fun org.gradle.api.tasks.AbstractCopyTask.`filter`(`filterType`: kotlin.reflect.KClass<java.io.FilterReader>, vararg `properties`: Pair<String, *>): org.gradle.api.tasks.AbstractCopyTask =
                    `filter`(mapOf(*`properties`), `filterType`.java)
                """,
                """
                @org.gradle.api.Incubating
                fun <T : org.gradle.api.Task> org.gradle.api.tasks.TaskContainer.`register`(`name`: String, `type`: kotlin.reflect.KClass<T>, `configurationAction`: T.() -> Unit): org.gradle.api.tasks.TaskProvider<T> =
                    `register`(`name`, `type`.java, `configurationAction`)
                """,
                """
                @Deprecated("Deprecated Gradle API")
                @org.gradle.api.Incubating
                fun <T : org.gradle.api.Task> org.gradle.api.tasks.TaskContainer.`createLater`(`name`: String, `type`: kotlin.reflect.KClass<T>, `configurationAction`: T.() -> Unit): org.gradle.api.tasks.TaskProvider<T> =
                    `createLater`(`name`, `type`.java, `configurationAction`)
                """)
            assertThat(it.readText(), allOf(extensions.map { containsMultiLineString(it) }))
        }

        run("assemble")

        existing("extract").let { extract ->
            unzipTo(extract, existing("build/libs/gradle-api-extensions.jar"))
            assertThat(
                extract.walkTopDown().filter { it.isFile }.map { it.relativeTo(extract).path }.toList(),
                hasItems(*listOf(
                    "org/gradle/kotlin/dsl/GeneratedGradleApiExtensionsMainKotlinDslApiExtensions.kt",
                    "org/gradle/kotlin/dsl/GeneratedGradleApiExtensionsMainKotlinDslApiExtensionsKt.class")
                    .map { it.replace('/', File.separatorChar) }.toTypedArray()))
        }
    }
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
