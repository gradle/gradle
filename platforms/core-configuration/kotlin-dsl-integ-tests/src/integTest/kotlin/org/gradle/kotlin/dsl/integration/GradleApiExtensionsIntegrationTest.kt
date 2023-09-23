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

package org.gradle.kotlin.dsl.integration

import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskCollection
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.jar.JarFile


class GradleApiExtensionsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @ToBeFixedForConfigurationCache(because = "test captures script reference")
    fun `Kotlin chooses withType extension specialized to container type`() {

        withBuildScript(
            """

            open class A
            open class B : A()

            inline fun <reified T> inferredTypeOf(value: T) = typeOf<T>().toString()

            task("test") {

                doLast {

                    val ca = container(A::class)
                    val cb = ca.withType<B>()
                    println(inferredTypeOf(ca))
                    println(inferredTypeOf(cb))

                    val oca: DomainObjectCollection<A> = ca
                    val ocb = oca.withType<B>()
                    println(inferredTypeOf(oca))
                    println(inferredTypeOf(ocb))

                    val tt = tasks.withType<Task>()
                    val td = tt.withType<Delete>()
                    println(inferredTypeOf(tt))
                    println(inferredTypeOf(td))
                }
            }
            """
        )

        assertThat(
            build("test", "-q").output,
            containsMultiLineString(
                """
                ${NamedDomainObjectContainer::class.qualifiedName}<Build_gradle.A>
                ${NamedDomainObjectCollection::class.qualifiedName}<Build_gradle.B>
                ${DomainObjectCollection::class.qualifiedName}<Build_gradle.A>
                ${DomainObjectCollection::class.qualifiedName}<Build_gradle.B>
                ${TaskCollection::class.qualifiedName}<${Task::class.qualifiedName}>
                ${TaskCollection::class.qualifiedName}<${Delete::class.qualifiedName}>
                """
            )
        )
    }

    @Test
    @ToBeFixedForConfigurationCache(because = "source dependency VCS mappings are defined")
    fun `can use Gradle API generated extensions in scripts`() {

        withFile(
            "init.gradle.kts",
            """
            allprojects {
                container(String::class)
            }
            """
        )

        withDefaultSettings().appendText(
            """
            sourceControl {
                vcsMappings {
                    withModule("some:thing") {
                        from(GitVersionControlSpec::class) {
                            url = uri("")
                        }
                    }
                }
            }
            """
        )

        withBuildScript(
            """
            container(String::class)
            apply(from = "plugin.gradle.kts")
            """
        )

        withFile(
            "plugin.gradle.kts",
            """
            import org.apache.tools.ant.filters.ReplaceTokens

            // Class<T> to KClass<T>
            objects.property(Long::class)

            // Groovy named arguments to vararg of Pair
            fileTree("dir" to "src", "excludes" to listOf("**/ignore/**", "**/.data/**"))

            // Class<T> + Action<T> to KClass<T> + T.() -> Unit
            tasks.register("foo", Copy::class) {
                from("src")
                into("dst")

                // Class<T> + Groovy named arguments to KClass<T> + vararg of Pair
                filter(ReplaceTokens::class, "foo" to "bar")
            }
            """
        )

        build("foo", "-I", "init.gradle.kts")
    }

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `can use Gradle API generated extensions in buildSrc`() {

        withKotlinBuildSrc()

        withFile(
            "buildSrc/src/main/kotlin/foo/FooTask.kt",
            """
            package foo

            import org.gradle.api.*
            import org.gradle.api.model.*
            import org.gradle.api.tasks.*

            import org.gradle.kotlin.dsl.*

            import javax.inject.Inject

            import org.apache.tools.ant.filters.ReplaceTokens

            abstract class FooTask : DefaultTask() {

                @get:Inject
                abstract val objects: ObjectFactory

                @TaskAction
                fun foo() {
                    objects.domainObjectContainer(Long::class)
                }
            }
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/foo/foo.gradle.kts",
            """
            package foo

            tasks.register("foo", FooTask::class)
            """
        )

        withBuildScript(
            """
            plugins {
                id("foo.foo")
            }
            """
        )

        build("foo")
    }

    @Test
    fun `generated jar contains Gradle API extensions sources and byte code and is reproducible`() {

        val guh = newDir("guh")

        withBuildScript("")

        executer.withGradleUserHomeDir(guh)
        executer.requireIsolatedDaemons()

        build("help")

        val generatedJar = generatedExtensionsJarFromGradleUserHome(guh)

        val (generatedSources, generatedClasses) = JarFile(generatedJar)
            .use { it.entries().toList().map { entry -> entry.name } }
            .filter { it.startsWith("org/gradle/kotlin/dsl/GradleApiKotlinDslExtensions") }
            .groupBy { it.substring(it.lastIndexOf('.')) }
            .let { it[".kt"]!! to it[".class"]!! }

        assertTrue(generatedSources.isNotEmpty())
        assertTrue(generatedClasses.size >= generatedSources.size)

        val generatedSourceCode = JarFile(generatedJar).use { jar ->
            generatedSources.joinToString("\n") { name ->
                jar.getInputStream(jar.getJarEntry(name)).bufferedReader().use { it.readText() }
            }
        }

        val extensions = listOf(
            "package org.gradle.kotlin.dsl",
            """
            inline fun <S : T, T : Any> org.gradle.api.DomainObjectSet<T>.`withType`(`type`: kotlin.reflect.KClass<S>): org.gradle.api.DomainObjectSet<S> =
                `withType`(`type`.java)
            """,
            """
            inline fun org.gradle.api.tasks.AbstractCopyTask.`filter`(`filterType`: kotlin.reflect.KClass<out java.io.FilterReader>, vararg `properties`: Pair<String, Any?>): org.gradle.api.tasks.AbstractCopyTask =
                `filter`(mapOf(*`properties`), `filterType`.java)
            """,
            """
            inline fun <T : org.gradle.api.Task> org.gradle.api.tasks.TaskContainer.`register`(`name`: String, `type`: kotlin.reflect.KClass<T>, `configurationAction`: org.gradle.api.Action<in T>): org.gradle.api.tasks.TaskProvider<T> =
                `register`(`name`, `type`.java, `configurationAction`)
            """,
            """
            inline fun <T : Any> org.gradle.api.plugins.ExtensionContainer.`create`(`name`: String, `type`: kotlin.reflect.KClass<T>, vararg `constructionArguments`: Any): T =
                `create`(`name`, `type`.java, *`constructionArguments`)
            """,
            """
            inline fun <T : org.gradle.api.Named> org.gradle.api.model.ObjectFactory.`named`(`type`: kotlin.reflect.KClass<T>, `name`: String): T =
                `named`(`type`.java, `name`)
            """
        )

        assertThat(
            generatedSourceCode,
            allOf(extensions.map { containsString(it.trimIndent()) })
        )

        assertThat(
            generatedSourceCode,
            not(containsString("\r"))
        )
    }

    private
    fun generatedExtensionsJarFromGradleUserHome(guh: File): File =
        Regex("^\\d.*").let { startsWithDigit ->
            guh.resolve("caches")
                .listFiles { f -> f.isDirectory && f.name.matches(startsWithDigit) }
                .single()
                .resolve("generated-gradle-jars")
                .listFiles { f -> f.isFile && f.name.startsWith("gradle-kotlin-dsl-extensions-") }
                .single()
        }
}
