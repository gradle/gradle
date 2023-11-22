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
import org.junit.Ignore
import org.junit.Test


class RestrictedPluginsBlockInterpreterTest : PluginsBlockInterpreterTest() {

    override fun assertStaticInterpretationOf(pluginsBlock: String, vararg specs: PluginRequestSpec) {
        assertThat(
            interpret(Program.Plugins(fragment("plugins", pluginsBlock)), isRestrictedDslOnly = true),
            equalTo(
                PluginsBlockInterpretation.Static(specs.asList())
            )
        )
    }

    @Test
    @Ignore // The `kotlin-dsl` syntax is not supported
    override fun `single plugin - kotlin-dsl`() {
        super.`single plugin - kotlin-dsl`()
    }

    @Test
    @Ignore // The `kotlin-dsl` syntax is not supported
    override fun `single plugin - kotlin-dsl apply`() {
        super.`single plugin - kotlin-dsl apply`()
    }

    /**
     * This is a copy of the function from the parent class with the only change being that the infix precedence is fixed in the test data
     */
    @Test
    override fun `single plugin - id() mixed version apply`() {
        assertStaticInterpretationOf(
            """
                id("plugin-id").version("1.0").apply(true)
                id("plugin-id").version("2.0") apply true
                id("plugin-id") version "3.0" apply(true)
                id("plugin-id") version "4.0" apply true

                id("plugin-id").apply(false).version("1.0")
                id("plugin-id").apply(false) version "2.0"
                id("plugin-id") apply false version("3.0")
                id("plugin-id") apply false version "4.0"
            """,
            PluginRequestSpec("plugin-id", version = "1.0", apply = true),
            PluginRequestSpec("plugin-id", version = "2.0", apply = true),
            PluginRequestSpec("plugin-id", version = "3.0", apply = true),
            PluginRequestSpec("plugin-id", version = "4.0", apply = true),
            PluginRequestSpec("plugin-id", version = "1.0", apply = false),
            PluginRequestSpec("plugin-id", version = "2.0", apply = false),
            PluginRequestSpec("plugin-id", version = "3.0", apply = false),
            PluginRequestSpec("plugin-id", version = "4.0", apply = false),
        )
    }
}
