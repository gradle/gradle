/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.internal.serialize.graph.StringPrefixedTree
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringPrefixedTreeTest {

    @Test
    fun `prefixed tree humble beginning`() {
        val prefixedTree = StringPrefixedTree()

        val fooKey = prefixedTree.insert(File("org/example/foo/Foo"))
        val barKey = prefixedTree.insert(File("org/example/bar/Bar"))

        assertEquals(prefixedTree.getByKey(fooKey), File("org/example/foo/Foo"))
        assertEquals(prefixedTree.getByKey(barKey), File("org/example/bar/Bar"))
    }

    @Test
    fun `prefixify strings`() {
        val prefixedTree = StringPrefixedTree()

        prefixedTree.insert(File("org/example/foo/Foo"))
        prefixedTree.insert(File("org/example/bar/Bar"))
        prefixedTree.insert(File("org/example/bar/Bar1"))

        val expectedTree = StringPrefixedTree.Node(
            0,
            "",
            mutableMapOf(
                "org" to StringPrefixedTree.Node(
                    1,
                    "org",
                    mutableMapOf(
                        "example" to StringPrefixedTree.Node(
                            2,
                            "example",
                            mutableMapOf(
                                "foo" to StringPrefixedTree.Node(
                                    3,
                                    "foo",
                                    mutableMapOf(
                                        "Foo" to StringPrefixedTree.Node(
                                            4,
                                            "Foo",
                                            mutableMapOf()
                                        )
                                    )
                                ),
                                "bar" to StringPrefixedTree.Node(
                                    5,
                                    "bar",
                                    mutableMapOf(
                                        "Bar" to StringPrefixedTree.Node(
                                            6,
                                            "Bar",
                                            mutableMapOf()
                                        ),
                                        "Bar1" to StringPrefixedTree.Node(
                                            7,
                                            "Bar1",
                                            mutableMapOf()
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(prefixedTree.root, expectedTree)
    }

    @Test
    fun `returns the same key for the same file`() {
        val prefixedTree = StringPrefixedTree()

        val foo1Key = prefixedTree.insert(File("org/example/foo/Foo"))
        val foo2Key = prefixedTree.insert(File("org/example/foo/Foo"))

        assertEquals(foo1Key, foo2Key)
    }
}
