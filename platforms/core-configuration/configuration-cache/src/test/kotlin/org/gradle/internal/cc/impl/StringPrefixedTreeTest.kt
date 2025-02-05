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
import org.junit.Ignore
import org.junit.Test
import java.io.File

class StringPrefixedTreeTest {

    @Test
    fun `prefixed tree humble beginning`() {
        val prefixedTree = StringPrefixedTree()
        val fooIndex = prefixedTree.insert(File("org/example/foo/Foo"))
        val barIndex = prefixedTree.insert(File("org/example/bar/Bar"))

        val indexes = prefixedTree.buildIndexes(prefixedTree.root)

        assertEquals(File("/org/example/foo/Foo"), indexes[fooIndex])
        assertEquals(File("/org/example/bar/Bar"), indexes[barIndex])
    }

    @Test
    fun `prefixify strings`() {
        val prefixedTree = StringPrefixedTree()
        prefixedTree.insert(File("org/example/foo/Foo"))
        prefixedTree.insert(File("org/example/bar")) // insert a dir
        prefixedTree.insert(File("org/example/bar/Bar"))
        prefixedTree.insert(File("org/example/bar/Bar1"))

        val expectedTree = StringPrefixedTree.Node(
            null,
            "",
            mutableListOf(
                StringPrefixedTree.Node(
                    null, "org",
                    mutableListOf(
                        StringPrefixedTree.Node(
                            null, "example",
                            mutableListOf(
                                StringPrefixedTree.Node(
                                    null, "foo",
                                    mutableListOf(
                                        StringPrefixedTree.Node(
                                            0,
                                            "Foo",
                                            mutableListOf()
                                        )
                                    )
                                ),
                                StringPrefixedTree.Node(
                                    1, "bar",
                                    mutableListOf(
                                        StringPrefixedTree.Node(
                                            2, "Bar",
                                            mutableListOf()
                                        ),
                                        StringPrefixedTree.Node(
                                            3, "Bar1",
                                            mutableListOf()
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(expectedTree, prefixedTree.root)
    }

    @Test
    @Ignore("wip")
    fun `tree is compressable`() {
        val prefixedTree = StringPrefixedTree()
        prefixedTree.insert(File("org/example/company/foo/Foo"))
        prefixedTree.insert(File("org/example/company/bar/Bar"))

        val expectedCompressedTree = StringPrefixedTree.Node(
            null,
            "org/example/company",
            mutableListOf(
                StringPrefixedTree.Node(0, "foo/Foo", mutableListOf()),
                StringPrefixedTree.Node(1, "bar/Bar", mutableListOf())
            )
        )

        assertEquals(expectedCompressedTree, prefixedTree.compress())
    }

    @Test
    @Ignore("wip")
    fun `inserted dirs are not compressable`() {
        val prefixedTree = StringPrefixedTree()
        prefixedTree.insert(File("org/example/company/foo"))
        prefixedTree.insert(File("org/example/company/foo/bar/zum/Zum"))

        val expectedCompressedTree = StringPrefixedTree.Node(
            null,
            "org/example/company",
            mutableListOf(
                StringPrefixedTree.Node(0, "foo", mutableListOf()),
                StringPrefixedTree.Node(1, "bar/zum/Zum", mutableListOf())
            )
        )

        assertEquals(expectedCompressedTree, prefixedTree.compress())
    }

    @Test
    fun `indexes are valid after compression`() {
        val prefixedTree = StringPrefixedTree()
        val fooIndex = prefixedTree.insert(File("org/example/foo/Foo"))
        val barIndex = prefixedTree.insert(File("org/example/bar/Bar"))

        val indexes = prefixedTree.buildIndexes(prefixedTree.compress())

        assertEquals(File("/org/example/foo/Foo"), indexes[fooIndex])
        assertEquals(File("/org/example/bar/Bar"), indexes[barIndex])
    }

    @Test
    fun `returns the same index for the same file`() {
        val prefixedTree = StringPrefixedTree()

        val foo1Index = prefixedTree.insert(File("org/example/foo/Foo"))
        val foo2Index = prefixedTree.insert(File("org/example/foo/Foo"))

        assertEquals(foo1Index, foo2Index)
    }
}
