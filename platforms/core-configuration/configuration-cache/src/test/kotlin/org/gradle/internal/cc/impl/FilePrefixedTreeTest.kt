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
    fun `can create prefixed tree of inserted files`() {
        val prefixedTree = FilePrefixedTree()
        prefixedTree.insert(File("org/example/foo/Foo"))
        prefixedTree.insert(File("org/example/bar")) // insert a dir
        prefixedTree.insert(File("org/example/bar/Bar"))
        prefixedTree.insert(File("org/example/bar/Bar1"))

        val expectedTree =
            root(
                intermediateNode(
                    "org", 1,
                    intermediateNode(
                        "example", 2,
                        intermediateNode(
                            "foo", 3,
                            finalNode(
                                "Foo", 4
                            )
                        ),
                        finalNode(
                            "bar", 5,
                            finalNode(
                                "Bar", 6
                            ),
                            finalNode(
                                "Bar1", 7
                            )
                        )
                    )
                )
            )

        assertEquals(expectedTree, prefixedTree.root)
    }

    @Test
    fun `absolute paths and relative paths in the same tree are supported`() {
        val prefixedTree = FilePrefixedTree()
        prefixedTree.insert(File("org/example/Foo"))
        prefixedTree.insert(File("/org/example/bar/Bar"))

        val expectedTree =
            root(
                intermediateNode(
                    "org", 1,
                    intermediateNode(
                        "example", 2,
                        finalNode(
                            "Foo", 3
                        )
                    )
                ),
                intermediateNode(
                    "/", 4,
                    intermediateNode(
                        "org", 5,
                        intermediateNode(
                            "example", 6,
                            intermediateNode(
                                "bar", 7,
                                finalNode(
                                    "Bar", 8
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
        prefixedTree.insert(File("/org/example/company/foo/Foo"))
        prefixedTree.insert(File("/org/example/company/bar/Bar"))
        prefixedTree.insert(File("/org/example/company/bar/Bar1"))
        prefixedTree.insert(File("org/example/company/bar"))
        prefixedTree.insert(File("org/example/company/bar/Bar2"))
        prefixedTree.insert(File("/org/example/company/baz/Baz"))

        val expectedCompressedTree =
            root(
                finalNode(
                    "org/example/company/bar", 13,
                    finalNode(
                        "Bar2", 14
                    )
                ),
                intermediateNode(
                    "/org/example/company", 4,
                    finalNode("baz/Baz", 16),
                    intermediateNode(
                        "bar", 7,
                        finalNode(
                            "Bar1", 9
                        ),
                        finalNode(
                            "Bar", 8
                        ),
                    ),
                    finalNode(
                        "foo/Foo", 6
                    )
                )
            )

        assertEquals(expectedCompressedTree, prefixedTree.compress())
    }

    @Test
    fun `indexes are preserved after compression`() {
        val prefixedTree = FilePrefixedTree()
        val fooIndex = prefixedTree.insert(File("org/example/foo/Foo"))
        val barIndex = prefixedTree.insert(File("org/example/bar/Bar"))

        val indexes = FilePrefixedTree.buildIndexes(prefixedTree.compress())

        assertEquals(File("org/example/foo/Foo"), indexes[fooIndex])
        assertEquals(File("org/example/bar/Bar"), indexes[barIndex])
    }

    @Test
    fun `returns the same index for the same file`() {
        val prefixedTree = FilePrefixedTree()

        val foo1Index = prefixedTree.insert(File("org/example/foo/Foo"))
        val foo2Index = prefixedTree.insert(File("org/example/foo/Foo"))

        assertEquals(foo1Index, foo2Index)
    }

    private fun node(isFinal: Boolean, index: Int, segment: String, vararg children: FilePrefixedTree.Node) =
        FilePrefixedTree.Node(isFinal, index, segment, children.associateBy { it.segment }.toMutableMap())

    private fun finalNode(segment: String, index: Int, vararg children: FilePrefixedTree.Node) =
        node(true, index, segment, *children)

    private fun intermediateNode(segment: String, index: Int, vararg children: FilePrefixedTree.Node) =
        node(false, index, segment, *children)

    private fun root(vararg children: FilePrefixedTree.Node) =
        intermediateNode("", 0, *children)
}
