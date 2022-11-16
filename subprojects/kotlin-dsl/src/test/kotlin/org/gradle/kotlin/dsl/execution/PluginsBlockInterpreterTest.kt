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
    fun `empty plugins block`() {
        assertStaticInterpretationOf("")
    }

    @Test
    fun `single plugin - id()`() {
        assertStaticInterpretationOf(
            """id("plugin-id")""",
            PluginRequestSpec("plugin-id")
        )
    }

    @Test
    fun `single plugin - id() version`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version "1.0"""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin - id() version()`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version("1.0")""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin - id() dot version()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0")""",
            PluginRequestSpec("plugin-id", version = "1.0")
        )
    }

    @Test
    fun `single plugin - id() apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id") apply false""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin - id() apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id") apply(false)""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin - id() dot apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").apply(false)""",
            PluginRequestSpec("plugin-id", apply = false)
        )
    }

    @Test
    fun `single plugin - id() version apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id") version "1.0" apply false""",
            PluginRequestSpec("plugin-id", version = "1.0", apply = false)
        )
    }

    @Test
    fun `single plugin - id() dot version() dot apply()`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0").apply(false)""",
            PluginRequestSpec("plugin-id", version = "1.0", apply = false)
        )
    }


    @Test
    fun `single plugin - id mixed version apply`() {
        assertStaticInterpretationOf(
            """id("plugin-id").version("1.0").apply(true) version "3.0" apply false""",
            PluginRequestSpec("plugin-id", version = "3.0", apply = false)
        )
    }

    @Test
    fun `single plugin - kotlin()`() {
        assertStaticInterpretationOf(
            """kotlin("jvm")""",
            PluginRequestSpec("org.jetbrains.kotlin.jvm")
        )
    }

    @Test
    fun `single plugin - kotlin() version apply false`() {
        assertStaticInterpretationOf(
            """kotlin("jvm") version "1.0" apply false""",
            PluginRequestSpec("org.jetbrains.kotlin.jvm", version = "1.0", apply = false)
        )
    }

    @Test
    fun `multiple plugins - id()`() {
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
    fun `multiple plugins - id() separated by semicolon`() {
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
    fun `multiple plugins - id() version apply mixed syntax`() {
        assertStaticInterpretationOf(
            """
                id("plugin-id-1") apply false ; id("plugin-id-2")
                kotlin("jvm") version "1.0" apply false
                ;
                id("plugin-id-3") version "2.0"
            """,
            PluginRequestSpec("plugin-id-1", apply = false),
            PluginRequestSpec("plugin-id-2"),
            PluginRequestSpec("org.jetbrains.kotlin.jvm", version = "1.0", apply = false),
            PluginRequestSpec("plugin-id-3", version = "2.0"),
        )
    }

    @Test
    fun `unsupported syntax - plugin spec accessor`() {
        assertDynamicInterpretationOf(
            """java""",
            "Expecting id or kotlin, got 'java'"
        )
    }

    @Test
    fun `unsupported syntax - version catalog alias`() {
        assertDynamicInterpretationOf(
            """alias(libs.plugins.jmh)""",
            "Expecting id or kotlin, got 'alias'"
        )
    }

    @Test
    fun `syntax error - starts with unknown identifier`() {
        assertDynamicInterpretationOf(
            """garbage""",
            "Expecting id or kotlin, got 'garbage'"
        )
    }

    @Test
    fun `syntax error - starts with unexpected token`() {
        assertDynamicInterpretationOf(
            """.""",
            "Expecting plugin spec, got '.'"
        )
    }

    @Test
    fun `syntax error - id() without parens`() {
        assertDynamicInterpretationOf(
            """id "plugin-id"""",
            "Expecting (, got '\"'"
        )
    }

    @Test
    fun `syntax error - id() with not a string`() {
        assertDynamicInterpretationOf(
            """id(false)""",
            "Expecting <plugin id string>, got 'false'"
        )
    }

    @Test
    fun `syntax error - id() with empty string`() {
        assertDynamicInterpretationOf(
            """id("")""",
            "Expecting <plugin id string>, got '\"'"
        )
    }

    @Test
    fun `syntax error - id() with unclosed string`() {
        assertDynamicInterpretationOf(
            """id("plugin-id-1) ; id("plugin-id-2")"""",
            "Expecting ), got 'plugin'"
        )
    }

    @Test
    fun `syntax error - id() with unclosed parens`() {
        assertDynamicInterpretationOf(
            """id("plugin-id-1" ; id("plugin-id-2")"""",
            "Expecting ), got ';'"
        )
    }

    @Test
    fun `syntax error - id() with misplaced semicolon`() {
        assertDynamicInterpretationOf(
            """id("plugin-id-1";)""",
            "Expecting ), got ';'"
        )
    }

    @Test
    fun `syntax error - id() apply with not a boolean`() {
        assertDynamicInterpretationOf(
            """id("plugin-id") apply "1.0"""",
            "Expecting (, got '\"'"
        )
    }

    @Test
    fun `syntax error - id() version with not a string`() {
        assertDynamicInterpretationOf(
            """id("plugin-id") version false""",
            "Expecting (, got 'false'"
        )
    }

    @Test
    fun `syntax error - id() id() on the same line`() {
        assertDynamicInterpretationOf(
            """id("plugin-id-1") id("plugin-id-2")""",
            "Expecting version or apply, got 'id'"
        )
    }

    @Test
    fun `syntax error - kotlin() with misplaced semicolon`() {
        assertDynamicInterpretationOf(
            """kotlin("jvm";)""",
            "Expecting ), got ';'"
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
    fun assertDynamicInterpretationOf(pluginsBlock: String, reason: String = "BOOM") {
        assertThat(
            interpret(Program.Plugins(fragment("plugins", pluginsBlock))),
            equalTo(
                PluginsBlockInterpretation.Dynamic(reason)
            )
        )
    }
}
