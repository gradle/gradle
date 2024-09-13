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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToStartOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.ElementNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.InsertNodesAfterNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.InsertNodesBeforeNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.PropertyNodeMutation.RenamePropertyNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ReplaceValue
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ValueFactoryNodeMutation.ValueNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutationFailureReason.TargetNotFoundOrSuperseded
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class DocumentTextMutationPlannerTest {
    private
    val planner = DocumentTextMutationPlanner()

    private
    val simpleDocument = convertBlockToDocument(
        ParseTestUtil.parseAsTopLevelBlock(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                k = 5
            }

            l(6)

            """.trimIndent()
        )
    )

    @Test
    fun `can plan removal of multiple nodes`() {
        val document = simpleDocument

        val plan = planner.planDocumentMutations(
            document, listOf(
                RemoveNode(document.elementNamed("f")),
                RemoveNode(document.elementNamed("g").elementNamed("j"))
            )
        )

        assertEquals(
            """

            g(2) {
                h(3)
                k = 5
            }

            l(6)

            """.trimIndent(), plan.newText
        )

        assertTrue { plan.unsuccessfulDocumentMutations.isEmpty() }
    }

    @Test
    fun `can plan renaming an element and removing a node inside its content at the same time`() {
        val plan = planner.planDocumentMutations(
            simpleDocument, listOf(
                ElementNodeCallMutation(simpleDocument.elementNamed("g"), CallMutation.RenameCall { "g0" }),
                RemoveNode(simpleDocument.elementNamed("g").elementNamed("j"))
            )
        )

        assertEquals(
            """
            f(1)

            g0(2) {
                h(3)
                k = 5
            }

            l(6)

            """.trimIndent(), plan.newText
        )
    }

    @Test
    fun `can plan renaming of multiple nodes of various kinds`() {
        val plan = planner.planDocumentMutations(
            simpleDocument, listOf(
                ElementNodeCallMutation(simpleDocument.elementNamed("f"), CallMutation.RenameCall { "f0" }),
                RenamePropertyNode(simpleDocument.elementNamed("g").propertyNamed("k")) { "k0" },
                ValueNodeCallMutation(
                    (simpleDocument.elementNamed("g").elementNamed("j").elementValues.single()) as ValueFactoryNode,
                    CallMutation.RenameCall { "jj0" }
                )
            )
        )

        assertEquals(
            """
            f0(1)

            g(2) {
                h(3)
                j(jj0(4))
                k0 = 5
            }

            l(6)

            """.trimIndent(), plan.newText
        )
    }

    @Test
    fun `removal and rename of an element in an already removed block is reported`() {
        val mutations = listOf(
            RemoveNode(simpleDocument.elementNamed("g")),
            RemoveNode(simpleDocument.elementNamed("g").propertyNamed("k")),
            RenamePropertyNode(simpleDocument.elementNamed("g").propertyNamed("k")) { "k0" }
        )
        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)


            l(6)

            """.trimIndent(), plan.newText
        )

        assertEquals(mutations.drop(1), plan.unsuccessfulDocumentMutations.map { it.mutation })
        assertEquals(listOf(TargetNotFoundOrSuperseded).let { listOf(it, it) }, plan.unsuccessfulDocumentMutations.map { it.failureReasons })
    }

    @Test
    fun `multiple renames get correctly stacked`() {
        val propertyNode = simpleDocument.elementNamed("g").propertyNamed("k")

        val mutations = listOf(
            RenamePropertyNode(propertyNode) { "k0" },
            RenamePropertyNode(propertyNode) { "k1" }
        )
        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                k1 = 5
            }

            l(6)

            """.trimIndent(), plan.newText
        )

        assertTrue { plan.unsuccessfulDocumentMutations.isEmpty() }
    }

    @Test
    fun `can plan node replacement`() {
        val mutations = listOf(
            ReplaceNode(simpleDocument.elementNamed("f")) { NewDocumentNodes(listOf(nodeFromText("f0(1)"))) },
            ReplaceNode(simpleDocument.elementNamed("g")) { NewDocumentNodes(listOf(nodeFromText("g0(2) { h0(3) }"))) }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f0(1)

            g0(2) {
                h0(3)
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `can plan node insertion before another node`() {
        val mutations = listOf(
            InsertNodesBeforeNode(simpleDocument.elementNamed("g")) { NewDocumentNodes(listOf(nodeFromText("newNode()"), nodeFromText("oneMore()"))) },
            InsertNodesBeforeNode(simpleDocument.elementNamed("g").elementNamed("j")) { NewDocumentNodes(listOf(nodeFromText("newNodeInBlock()"), nodeFromText("oneMore()"))) }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            newNode()

            oneMore()

            g(2) {
                h(3)
                newNodeInBlock()
                oneMore()
                j(jj(4))
                k = 5
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `can plan node insertion after another node`() {
        val mutations = listOf(
            InsertNodesAfterNode(simpleDocument.elementNamed("g")) { NewDocumentNodes(listOf(nodeFromText("newNode()"), nodeFromText("oneMore()"))) },
            InsertNodesAfterNode(simpleDocument.elementNamed("g").elementNamed("j")) { NewDocumentNodes(listOf(nodeFromText("newNodeInBlock()"), nodeFromText("oneMore()"))) }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                newNodeInBlock()
                oneMore()
                k = 5
            }

            newNode()

            oneMore()

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `can plan node insertion into the block start`() {
        val mutations = listOf(
            AddChildrenToStartOfBlock(simpleDocument.elementNamed("g")) {
                NewDocumentNodes(listOf(nodeFromText("newNode1()"), nodeFromText("newNode2()")))
            }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                newNode1()
                newNode2()
                h(3)
                j(jj(4))
                k = 5
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `can plan node insertion into the block end`() {
        val mutations = listOf(
            AddChildrenToEndOfBlock(simpleDocument.elementNamed("g")) { NewDocumentNodes(listOf(nodeFromText("newNode1()"), nodeFromText("newNode2()"))) },
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                k = 5
                newNode1()
                newNode2()
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }


    @Test
    fun `can plan node insertion into an element that does not have content yet`() {
        val mutations = listOf(
            AddChildrenToStartOfBlock(simpleDocument.elementNamed("f")) {
                NewDocumentNodes(listOf(nodeFromText("newNode1()"), nodeFromText("newNode2()")))
            },
            AddChildrenToEndOfBlock(simpleDocument.elementNamed("l")) {
                NewDocumentNodes(listOf(nodeFromText("newNode3()"), nodeFromText("newNode4()")))
            },
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1) {
                newNode1()
                newNode2()
            }

            g(2) {
                h(3)
                j(jj(4))
                k = 5
            }

            l(6) {
                newNode3()
                newNode4()
            }

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `of two mutations replacing a single node the first is applied and the second is reported as superseded`() {
        val f = simpleDocument.elementNamed("f")
        val mutations = listOf(
            ReplaceNode(f) { NewDocumentNodes(listOf(nodeFromText("newNode1()"))) },
            ReplaceNode(f) { NewDocumentNodes(listOf(nodeFromText("newNode2()"))) },
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            newNode1()

            g(2) {
                h(3)
                j(jj(4))
                k = 5
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `insertions into an empty element get merged together`() {
        val f = simpleDocument.elementNamed("f")
        val mutations = listOf(
            // Also provide them mixed wrt start and end:
            AddChildrenToEndOfBlock(f) { NewDocumentNodes(listOf(nodeFromText("newNode3()"))) }, // expected to go before newNode4() and after newNode2()
            AddChildrenToStartOfBlock(f) { NewDocumentNodes(listOf(nodeFromText("newNode1()"))) },
            AddChildrenToStartOfBlock(f) { NewDocumentNodes(listOf(nodeFromText("newNode2()"))) }, // expected to go before newNode1()
            AddChildrenToEndOfBlock(f) { NewDocumentNodes(listOf(nodeFromText("newNode4()"))) },
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1) {
                newNode2()
                newNode1()
                newNode3()
                newNode4()
            }

            g(2) {
                h(3)
                j(jj(4))
                k = 5
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `can simultaneously rename a property and replace its value`() {
        val property = simpleDocument.elementNamed("g").propertyNamed("k")
        val mutations = listOf(
            RenamePropertyNode(property) { "k0" },
            ReplaceValue(property.value) { valueFromText("foo0(5)") }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                k0 = foo0(5)
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )
    }

    @Test
    fun `replacement of a value that has already been replaced is reported`() {
        val property = simpleDocument.elementNamed("g").propertyNamed("k")
        val mutations = listOf(
            ReplaceValue(property.value) { valueFromText("replaced1()") },
            ReplaceValue(property.value) { valueFromText("replaced2()") }
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            f(1)

            g(2) {
                h(3)
                j(jj(4))
                k = replaced1()
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )

        with(plan.unsuccessfulDocumentMutations.single()) {
            assertEquals(mutations[1], mutation)
            assertEquals(listOf(TargetNotFoundOrSuperseded), this.failureReasons)
        }
    }

    @Test
    fun `can apply all kinds of mutation to an element parts without conflicts`() {
        val f = simpleDocument.elementNamed("f")
        val g = simpleDocument.elementNamed("g")

        val mutations = listOf(
            // For an element with non-empty content:
            ElementNodeCallMutation(g, CallMutation.RenameCall { "gRenamed" }),
            ReplaceValue(g.elementValues[0]) { valueFromText("replaced()") },
            ReplaceNode(g.elementNamed("h")) { NewDocumentNodes(listOf(nodeFromText("hReplaced()"))) },
            ReplaceValue(g.elementNamed("j").elementValues[0]) { valueFromText("alsoReplaced()") },

            // And also for an element with empty content:
            ElementNodeCallMutation(f, CallMutation.RenameCall { "fRenamed" }),
            AddChildrenToEndOfBlock(f) { NewDocumentNodes(listOf(nodeFromText("added()"))) },
            ReplaceValue(f.elementValues[0]) { valueFromText("replaced()") },
        )

        val plan = planner.planDocumentMutations(simpleDocument, mutations)

        assertEquals(
            """
            fRenamed(replaced()) {
                added()
            }

            gRenamed(replaced()) {
                hReplaced()
                j(alsoReplaced())
                k = 5
            }

            l(6)

            """.trimIndent(),
            plan.newText
        )

        assertTrue { plan.unsuccessfulDocumentMutations.isEmpty() }
    }

    private
    fun nodeFromText(code: String): DocumentNode =
        convertBlockToDocument(ParseTestUtil.parseAsTopLevelBlock(code)).content.single()

    private
    fun valueFromText(code: String): DeclarativeDocument.ValueNode =
        (convertBlockToDocument(ParseTestUtil.parseAsTopLevelBlock("stub = $code")).content.single() as PropertyNode).value
}
