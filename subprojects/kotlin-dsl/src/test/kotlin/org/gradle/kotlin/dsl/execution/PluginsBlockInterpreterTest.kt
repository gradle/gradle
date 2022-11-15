/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.gradle.kotlin.dsl.execution.ResidualProgram.PluginRequestSpec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Test


class PluginsBlockInterpreterTest {

    @Test
    fun `single plugin id`() {
        assertStaticInterpretationOf(
            """id("plugin-id")""",
            PluginRequestSpec("plugin-id")
        )
    }

    @Test
    fun `single plugin id version`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version "1.0"""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin id version()`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version("1.0")""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin id dot version()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0")""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin id apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id") apply false""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin id apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id") apply(false)""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin id dot apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").apply(false)""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin id version apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version "1.0" apply false""",
            PluginRequestSpec("plugin-id", version = "1.0", apply = false)
        )
    }

    @Test
    fun `single plugin id dot version() dot apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0").apply(false)""",
            PluginRequestSpec("plugin-id", version = "1.0", apply = false)
        )
    }


    @Test
    fun `single plugin id mixed version apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0").apply(true) version "3.0" apply false""",
            PluginRequestSpec("plugin-id", version = "3.0", apply = false)
        )
    }

    @Test
    fun `single plugin id apply syntax error`() {
        assertDynamicInterpretationOf("""id("plugin-id") apply "1.0"""")
    }

    @Test
    fun `single plugin id version syntax error`() {
        assertDynamicInterpretationOf("""id("plugin-id") version false""")
    }

    @Test
    fun `multiple plugin ids`() {
        assertStaticInterpretationOf(
            """
                id("plugin-id-1")
                id("plugin-id-2")
            """,
            PluginRequestSpec("plugin-id-1"),
            PluginRequestSpec("plugin-id-2")
        )
    }

    @Test
    fun `multiple plugin ids separated by semicolon`() {
        assertStaticInterpretationOf(
            """
                id("plugin-id-1") ; id("plugin-id-2")
                ;
            """,
            PluginRequestSpec("plugin-id-1"),
            PluginRequestSpec("plugin-id-2")
        )
    }

    @Test
    fun `single plugin id syntax error`() {
        assertDynamicInterpretationOf(
            """
                id("plugin-id-1";)
            """
        )
    }

    @Test
    fun `multiple plugin ids syntax error`() {
        assertDynamicInterpretationOf(
            """
                id("plugin-id-1") id("plugin-id-2")
            """
        )
    }

    private
    fun assertStaticInterpretationOf(pluginsBlock: String, vararg specs: PluginRequestSpec) {
        assertThat(
            interpret(Program.Plugins(fragment("plugins", pluginsBlock))),
            equalTo(
                PluginsBlockInterpretation.Static(specs.asList())
            )
        )
    }

    private
    fun assertDynamicInterpretationOf(pluginsBlock: String) {
        assertThat(
            interpret(Program.Plugins(fragment("plugins", pluginsBlock))),
            instanceOf(PluginsBlockInterpretation.Dynamic::class.java)
        )
    }
}
