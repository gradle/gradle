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

package org.gradle.kotlin.dsl.accessors

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions

import org.gradle.kotlin.dsl.concurrent.withSynchronousIO
import org.gradle.kotlin.dsl.fixtures.assertFailsWith

import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.pluginDescriptorEntryFor
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.pluginEntriesFrom

import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.kotlin.dsl.support.zipTo

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File
import java.util.zip.ZipException


class PluginSpecBuilderAccessorsClassPathTest : TestWithClassPath() {

    @Test
    fun `exception caused by empty jar carries file information`() {
        // given:
        val emptyJar = file("plugins.jar").apply {
            createNewFile()
        }

        // when:
        val exception = assertFailsWith(IllegalArgumentException::class) {
            pluginEntriesFrom(emptyJar)
        }

        // then:
        assertThat(
            exception.localizedMessage,
            containsString(emptyJar.name)
        )
        assertThat(
            exception.cause,
            instanceOf(ZipException::class.java)
        )
    }

    @Test
    fun `#buildPluginDependencySpecAccessorsFor`() {

        // given:
        val pluginsJar = jarWithPluginDescriptors(
            file("plugins.jar"),
            "my-plugin" to "MyPlugin",
            "my.own.plugin" to "my.own.Plugin"
        )

        val srcDir = newFolder("src")
        val binDir = newFolder("bin")

        // when:
        withSynchronousIO {
            buildPluginDependencySpecAccessorsFor(
                pluginDescriptorsClassPath = classPathOf(pluginsJar),
                srcDir = srcDir,
                binDir = binDir
            )
        }

        // then:
        val generatedAccessors = String(srcDir.resolve("org/gradle/kotlin/dsl/PluginDependencySpecAccessors.kt").readBytes())
        assertThat(
            generatedAccessors,
            allOf(
                not(containsString("\r")),
                containsString("import MyPlugin"),
                containsMultiLineString(
                    """

                    /**
                     * The `my` plugin group.
                     */
                    @org.gradle.api.Generated
                    class `MyPluginGroup`(internal val plugins: PluginDependenciesSpec)


                    /**
                     * Plugin ids starting with `my`.
                     */
                    val `PluginDependenciesSpec`.`my`: `MyPluginGroup`
                        get() = `MyPluginGroup`(this)


                    /**
                     * The `my.own` plugin group.
                     */
                    @org.gradle.api.Generated
                    class `MyOwnPluginGroup`(internal val plugins: PluginDependenciesSpec)


                    /**
                     * Plugin ids starting with `my.own`.
                     */
                    val `MyPluginGroup`.`own`: `MyOwnPluginGroup`
                        get() = `MyOwnPluginGroup`(plugins)


                    /**
                     * The `my.own.plugin` plugin implemented by [my.own.Plugin].
                     */
                    val `MyOwnPluginGroup`.`plugin`: PluginDependencySpec
                        get() = plugins.id("my.own.plugin")
                    """
                )
            )
        )

        // and:
        classLoaderFor(binDir).useToRun {
            val className = "org.gradle.kotlin.dsl.PluginDependencySpecAccessorsKt"
            val accessorsClass = loadClass(className)
            assertThat(
                accessorsClass.declaredMethods.map { it.name },
                hasItems("getMy", "getOwn", "getPlugin")
            )

            val expectedPluginSpec = mock<PluginDependencySpec>()
            val plugins = mock<PluginDependenciesSpec> {
                on { id(any()) } doReturn expectedPluginSpec
            }

            accessorsClass.run {

                val myPluginGroup =
                    getDeclaredMethod("getMy", PluginDependenciesSpec::class.java)
                        .invoke(null, plugins)!!

                val myOwnPluginGroup =
                    getDeclaredMethod("getOwn", myPluginGroup.javaClass)
                        .invoke(null, myPluginGroup)!!

                val actualPluginSpec =
                    getDeclaredMethod("getPlugin", myOwnPluginGroup.javaClass)
                        .invoke(null, myOwnPluginGroup) as PluginDependencySpec

                assertThat(
                    actualPluginSpec,
                    sameInstance(expectedPluginSpec)
                )
            }

            verify(plugins).id("my.own.plugin")
            verifyNoMoreInteractions(plugins)
        }
    }

    private
    fun jarWithPluginDescriptors(file: File, vararg pluginIdsToImplClasses: Pair<String, String>) =
        file.also {
            zipTo(
                it,
                pluginIdsToImplClasses.asSequence().map { (id, implClass) ->
                    pluginDescriptorEntryFor(id, implClass)
                }
            )
        }
}
