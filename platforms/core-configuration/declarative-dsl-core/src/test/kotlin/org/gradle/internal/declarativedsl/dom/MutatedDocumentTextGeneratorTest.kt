/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.dom

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes.Companion.empty
import org.gradle.internal.declarativedsl.dom.writing.MutatedDocumentTextGenerator
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTree.ChildTag.BlockElement
import org.gradle.internal.declarativedsl.dom.writing.TextPreservingTreeBuilder
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class MutatedDocumentTextGeneratorTest {
    // This one does not include dot-access like z.f(...), TODO: add some once it is fixed for the DOM
    private
    val simpleCodeWithComments = """
        // some comment
        myFun {
            a = 1 // some inline comment
            b = f("x", f("y"))
            // before x
            x = 1 // after x
            c = true
            nested {
                x = "y"
                z = "y"
            }
            factory(1)
        }

        myOtherFun {
            x = "y"
            y = "z"
        }

        oneMoreFun {
            // comment
            x = "y"
            y = "z"
        }

        x = 1
        y = 2

        block {
            test(); z = 1
        }
    """.trimIndent()

    private
    val simpleCodeForAddition = """
        myFun {
            a = 1
            nested {
                x = "y"
                y = 1
            }
            factory(1)
        }
    """.trimIndent()

    private
    val codeForAdditionWithProperties = """
        a = 1
        b = 2

        f {
            x = 1
            y = 2
        }

        h {
            x = 1
            y = 2
        }

        c = 3
        d = 4
    """.trimIndent()


    @Test
    fun `text from text-preserving tree is equal to the original text`() {
        // TODO: address the blank line in the end
        assertEquals(simpleCodeWithComments + "\n", generateCodeFrom(simpleCodeWithComments) { tree ->
            generateText(tree)
        })
    }

    @Test
    fun `can replace values`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(tree, replaceValue = { tag -> tag.valueNode.let { if (it is DeclarativeDocument.ValueNode.LiteralValueNode && it.value == 1) parseAsValue("test()") else null } })
        }

        assertEquals(
            """
            myFun {
                a = test()
                nested {
                    x = "y"
                    y = test()
                }
                factory(test())
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by renaming and removing nodes`() {
        val result = generateCodeFrom(simpleCodeWithComments) { tree ->
            generateText(tree, mapNames = { _, name -> name + "1" }, removeNodeIf = {
                if (it !is BlockElement) return@generateText false
                val documentNode = it.documentNode
                documentNode is PropertyNode && (documentNode.name == "x" || documentNode.name == "y")
            })
        }
        assertEquals(
            """
            // some comment
            myFun1 {
                a1 = 1 // some inline comment
                b1 = f1("x", f1("y"))
                // before x
                 // after x
                c1 = true
                nested1 {
                    z1 = "y"
                }
                factory1(1)
            }

            myOtherFun1 {
            }

            oneMoreFun1 {
                // comment
            }


            block1 {
                test1(); z1 = 1
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by inserting a node before the first element in a block`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree, insertNodesBefore = { childTag ->
                    if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" })
                        newNodesOf(syntheticElement)
                    else empty
                }
            )
        }

        assertEquals(
            """
            myFun {
                a = 1
                nested {
                    f(1, 2, g(3, 4, 5)) {
                        x = "test"
                    }
                    x = "y"
                    y = 1
                }
                factory(1)
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by inserting a node before non-first element in a block`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree,
                insertNodesBefore = { childTag ->
                    if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "y" })
                        newNodesOf(syntheticElement)
                    else empty
                }
            )
        }

        assertEquals(
            """
            myFun {
                a = 1
                nested {
                    x = "y"
                    f(1, 2, g(3, 4, 5)) {
                        x = "test"
                    }
                    y = 1
                }
                factory(1)
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by inserting a node after the last element in a block`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree,
                insertNodesAfter = { childTag ->
                    if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "y" })
                        newNodesOf(syntheticElement)
                    else empty
                }
            )
        }

        assertEquals(
            """
            myFun {
                a = 1
                nested {
                    x = "y"
                    y = 1
                    f(1, 2, g(3, 4, 5)) {
                        x = "test"
                    }
                }
                factory(1)
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by inserting a node after non-last element in a block`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree,
                insertNodesAfter = { childTag ->
                    if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" }) {
                        newNodesOf(syntheticElement)
                    } else empty
                }
            )
        }

        assertEquals(
            """
            myFun {
                a = 1
                nested {
                    x = "y"
                    f(1, 2, g(3, 4, 5)) {
                        x = "test"
                    }
                    y = 1
                }
                factory(1)
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can mutate document by inserting a node after the last element on the top level`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree,
                insertNodesAfter = { childTag ->
                    if (childTag.isNodeMatching { it is ElementNode && it.name == "myFun" })
                        newNodesOf(syntheticElement)
                    else empty
                }
            )
        }

        assertEquals(
            """
            myFun {
                a = 1
                nested {
                    x = "y"
                    y = 1
                }
                factory(1)
            }

            f(1, 2, g(3, 4, 5)) {
                x = "test"
            }

            """.trimIndent(), result
        )
    }


    @Test
    fun `can mutate document by inserting a node before the first element on the top level`() {
        val result = generateCodeFrom(simpleCodeForAddition) { tree ->
            generateText(
                tree,
                insertNodesBefore = { childTag ->
                    if (childTag.isNodeMatching { it is ElementNode && it.name == "myFun" })
                        newNodesOf(syntheticElement)
                    else empty
                }
            )
        }

        assertEquals(
            """
            f(1, 2, g(3, 4, 5)) {
                x = "test"
            }

            myFun {
                a = 1
                nested {
                    x = "y"
                    y = 1
                }
                factory(1)
            }

            """.trimIndent(), result
        )
    }

    @Test
    fun `can add multiple statements in a block before the first element with proper formatting`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesBefore = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }

        assertEquals(
            """
            a = 1
            b = 2

            f {
                s(1)
                t(2)
                x = 1
                y = 2
            }

            h {
                s(1)
                t(2)
                x = 1
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }


    @Test
    fun `can add multiple statements in a block after non-first element with proper formatting`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }

        assertEquals(
            """
            a = 1
            b = 2

            f {
                x = 1
                s(1)
                t(2)
                y = 2
            }

            h {
                x = 1
                s(1)
                t(2)
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `can add multiple statements in a block after the last element with proper formatting`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "y" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }
        assertEquals(
            """
            a = 1
            b = 2

            f {
                x = 1
                y = 2
                s(1)
                t(2)
            }

            h {
                x = 1
                y = 2
                s(1)
                t(2)
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `can add multiple statements on the top level before the first element with proper formatting`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesBefore = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }

        assertEquals(
            """
            a = 1
            b = 2

            f {
                s(1)
                t(2)
                x = 1
                y = 2
            }

            h {
                s(1)
                t(2)
                x = 1
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }


    @Test
    fun `can add multiple statements on the top level after non-first element with proper formatting`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "x" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }

        assertEquals(
            """
            a = 1
            b = 2

            f {
                x = 1
                s(1)
                t(2)
                y = 2
            }

            h {
                x = 1
                s(1)
                t(2)
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `when inserting elements after a property and before another property on the top level, blank lines are added around the elements`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "a" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }
        assertEquals(
            """
            a = 1

            s(1)

            t(2)

            b = 2

            f {
                x = 1
                y = 2
            }

            h {
                x = 1
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `when inserting properties between a property and an element on the top level, blank line is preserved before the element`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is PropertyNode && node.name == "b" })
                    NewDocumentNodes(multiProperties.toList())
                else empty
            })
        }
        // Having a line break between 'b = 2' and 't = 2' is not perfect but is OK for now.
        assertEquals(
            """
            a = 1
            b = 2

            s = 1
            t = 2

            f {
                x = 1
                y = 2
            }

            h {
                x = 1
                y = 2
            }

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `when inserting properties after an element and before a property on the top level, a blank line is preserved after the element`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is ElementNode && node.name == "h" })
                    NewDocumentNodes(multiProperties.toList())
                else empty
            })
        }
        // The blank line after t = 2 is OK for now: it seems that we must reuse the original line break before 'c = 3'
        assertEquals(
            """
            a = 1
            b = 2

            f {
                x = 1
                y = 2
            }

            h {
                x = 1
                y = 2
            }

            s = 1
            t = 2

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }

    @Test
    fun `when inserting elements after an element and before a property on the top level, a blank line is preserved after the element`() {
        val result = generateCodeFrom(codeForAdditionWithProperties) { tree ->
            generateText(tree, insertNodesAfter = { childTag ->
                if (childTag.isNodeMatching { node -> node is ElementNode && node.name == "h" })
                    NewDocumentNodes(multiElements.toList())
                else empty
            })
        }
        assertEquals(
            """
            a = 1
            b = 2

            f {
                x = 1
                y = 2
            }

            h {
                x = 1
                y = 2
            }

            s(1)

            t(2)

            c = 3
            d = 4

            """.trimIndent(), result
        )
    }


    private
    val syntheticElement = convertBlockToDocument(parseAsTopLevelBlock("f(1, 2, g(3, 4, 5)) { x = \"test\" }")).content.single()

    private
    val multiElements = convertBlockToDocument(parseAsTopLevelBlock("s(1)\nt(2)")).content

    private
    val multiProperties = convertBlockToDocument(parseAsTopLevelBlock("s = 1\nt = 2")).content

    private
    fun generateCodeFrom(code: String, generate: MutatedDocumentTextGenerator.(TextPreservingTree) -> String): String {
        val tree = parseAsTopLevelBlock(code)
        val dom = convertBlockToDocument(tree)
        val textTree = TextPreservingTreeBuilder().build(dom)
        return MutatedDocumentTextGenerator().generate(textTree)
    }

    private
    fun parseAsValue(code: String) = (convertBlockToDocument(parseAsTopLevelBlock("f($code)")).content.single() as ElementNode).elementValues.single()

    private
    fun TextPreservingTree.ChildTag.isNodeMatching(predicate: (DeclarativeDocument.DocumentNode) -> Boolean): Boolean =
        this is BlockElement && predicate(this.documentNode)

    private
    fun newNodesOf(vararg nodes: DeclarativeDocument.DocumentNode) =
        NewDocumentNodes(nodes.toList())
}
