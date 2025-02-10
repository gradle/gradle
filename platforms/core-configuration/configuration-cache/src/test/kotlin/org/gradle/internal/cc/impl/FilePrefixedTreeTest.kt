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

import org.gradle.internal.serialize.graph.FilePrefixedTree
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FilePrefixedTreeTest {

    @Test
    fun `prefixed tree humble beginning`() {
        val prefixedTree = FilePrefixedTree()
        val fooIndex = prefixedTree.insert(File("org/example/foo/Foo"))
        val barIndex = prefixedTree.insert(File("org/example/bar/Bar"))

        val indexes = prefixedTree.buildIndexes(prefixedTree.root)

        assertEquals(File("/org/example/foo/Foo"), indexes[fooIndex])
        assertEquals(File("/org/example/bar/Bar"), indexes[barIndex])
    }

    @Test
    fun `prefixify strings`() {
        val prefixedTree = FilePrefixedTree()
        prefixedTree.insert(File("org/example/foo/Foo"))
        prefixedTree.insert(File("org/example/bar")) // insert a dir
        prefixedTree.insert(File("org/example/bar/Bar"))
        prefixedTree.insert(File("org/example/bar/Bar1"))

        val expectedTree = FilePrefixedTree.Node(
            null,
            "",
            mutableMapOf(
                "org" to FilePrefixedTree.Node(
                    null, "org",
                    mutableMapOf(
                        "example" to FilePrefixedTree.Node(
                            null, "example",
                            mutableMapOf(
                                "foo" to FilePrefixedTree.Node(
                                    null, "foo",
                                    mutableMapOf(
                                        "Foo" to FilePrefixedTree.Node(
                                            0,
                                            "Foo",
                                        )
                                    )
                                ),
                                "bar" to FilePrefixedTree.Node(
                                    1, "bar",
                                    mutableMapOf(
                                        "Bar" to FilePrefixedTree.Node(
                                            2, "Bar",
                                            mutableMapOf()
                                        ),
                                        "Bar1" to FilePrefixedTree.Node(
                                            3, "Bar1",
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

        assertEquals(expectedTree, prefixedTree.root)
    }

    @Test
    fun `tree is compressable`() {
        val prefixedTree = FilePrefixedTree()
        prefixedTree.insert(File("org/example/company/foo/Foo"))
        prefixedTree.insert(File("org/example/company/bar/Bar"))

        val expectedCompressedTree = FilePrefixedTree.Node(
            null,
            "org/example/company",
            mutableMapOf(
                "foo/Foo" to FilePrefixedTree.Node(0, "foo/Foo"),
                "bar/Bar" to FilePrefixedTree.Node(1, "bar/Bar")
            )
        )

        assertEquals(expectedCompressedTree, prefixedTree.compress())
    }

    @Test
    fun `inserted dirs are not compressable`() {
        val prefixedTree = FilePrefixedTree()
        prefixedTree.insert(File("org/example/company/foo"))
        prefixedTree.insert(File("org/example/company/foo/bar/zum/Zum"))

        val expectedCompressedTree = FilePrefixedTree.Node(
            null, "org/example/company",
            mutableMapOf(
                "foo" to FilePrefixedTree.Node(
                    0, "foo",
                    mutableMapOf(
                        "bar/zum/Zum" to FilePrefixedTree.Node(
                            1, "bar/zum/Zum",
                        )
                    )
                ),
            )
        )

        assertEquals(expectedCompressedTree, prefixedTree.compress())
    }

    @Test
    fun `indexes are valid after compression`() {
        val prefixedTree = FilePrefixedTree()
        val fooIndex = prefixedTree.insert(File("org/example/foo/Foo"))
        val barIndex = prefixedTree.insert(File("org/example/bar/Bar"))

        val indexes = prefixedTree.buildIndexes(prefixedTree.compress())

        assertEquals(File("/org/example/foo/Foo"), indexes[fooIndex])
        assertEquals(File("/org/example/bar/Bar"), indexes[barIndex])
    }

    @Test
    fun `returns the same index for the same file`() {
        val prefixedTree = FilePrefixedTree()

        val foo1Index = prefixedTree.insert(File("org/example/foo/Foo"))
        val foo2Index = prefixedTree.insert(File("org/example/foo/Foo"))

        assertEquals(foo1Index, foo2Index)
    }
}
