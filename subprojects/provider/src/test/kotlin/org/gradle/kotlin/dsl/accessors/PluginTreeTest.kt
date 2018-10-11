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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class PluginTreeTest {

    @Test
    fun `PluginTree#of`() {

        val flatPlugin = PluginTree.PluginSpec("flat-plugin", "FlatPlugin")
        val nestedPluginA = PluginTree.PluginSpec("nested.plugin-a", "NestedPluginA")
        val nestedPluginB = PluginTree.PluginSpec("nested.other.plugin-b", "NestedPluginB")
        val nestedPluginC = PluginTree.PluginSpec("nested.other.plugin-c", "NestedPluginC")
        assertThat(
            PluginTree.of(
                listOf(
                    flatPlugin,
                    nestedPluginA,
                    nestedPluginB,
                    nestedPluginC,
                    // plugins with ids prefixed by other plugin ids
                    // cannot be properly supported due to the side-effecting nature
                    // of `PluginDependenciesSpec#id`
                    PluginTree.PluginSpec("flat-plugin.conflict", "IgnoredPlugin"),
                    PluginTree.PluginSpec("nested.plugin-a.conflict", "IgnoredPlugin")
                ).shuffled().asSequence()
            ),
            equalTo<Map<String, PluginTree>>(
                linkedMapOf(
                    flatPlugin.id to flatPlugin,
                    "nested" to PluginTree.PluginGroup(
                        listOf("nested"),
                        linkedMapOf(
                            "plugin-a" to nestedPluginA,
                            "other" to PluginTree.PluginGroup(
                                listOf("nested", "other"),
                                linkedMapOf(
                                    "plugin-b" to nestedPluginB,
                                    "plugin-c" to nestedPluginC
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
