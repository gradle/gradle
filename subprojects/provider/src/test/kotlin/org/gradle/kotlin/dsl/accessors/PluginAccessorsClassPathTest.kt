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

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath
import org.gradle.kotlin.dsl.fixtures.toPlatformLineSeparators
import org.gradle.kotlin.dsl.support.zipTo

import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class PluginAccessorsClassPathTest : TestWithClassPath() {

    @Test
    fun `#buildPluginAccessorsFor`() {

        // given:
        val pluginsJar = file("plugins.jar").also {
            zipTo(it, sequenceOf(
                "META-INF/gradle-plugins/my.own.plugin.properties" to "implementation-class=my.Plugin".toByteArray()
            ))
        }

        val srcDir = newFolder("src")
        val binDir = newFolder("bin")

        // when:
        buildPluginAccessorsFor(
            pluginDescriptorsClassPath = classPathOf(pluginsJar),
            accessorsCompilationClassPath = testCompilationClassPath,
            srcDir = srcDir,
            binDir = binDir
        )

        // then:
        assertThat(
            srcDir.resolve("org/gradle/kotlin/dsl/PluginAccessors.kt").readText().toPlatformLineSeparators(),
            containsMultiLineString("""

                /**
                 * The `my` plugin group.
                 */
                class `MyPluginGroup`(internal val plugins: PluginDependenciesSpec)


                /**
                 * Plugin ids starting with `my`.
                 */
                val `PluginDependenciesSpec`.`my`: `MyPluginGroup`
                    get() = `MyPluginGroup`(this)


                /**
                 * The `my.own` plugin group.
                 */
                class `MyOwnPluginGroup`(internal val plugins: PluginDependenciesSpec)


                /**
                 * Plugin ids starting with `my.own`.
                 */
                val `MyPluginGroup`.`own`: `MyOwnPluginGroup`
                    get() = `MyOwnPluginGroup`(plugins)


                /**
                 * The `my.own.plugin` plugin implemented by [my.Plugin].
                 */
                val `MyOwnPluginGroup`.`plugin`: PluginDependencySpec
                    get() = plugins.id("my.own.plugin")
            """)
        )
    }
}
