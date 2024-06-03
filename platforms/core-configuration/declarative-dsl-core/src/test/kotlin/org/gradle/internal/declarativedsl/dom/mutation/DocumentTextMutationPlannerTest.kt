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

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.ValueFactoryNode
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.ElementNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.PropertyNodeMutation.RenamePropertyNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ValueFactoryNodeMutation.ValueNodeCallMutation
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutationFailureReason.TargetNotFoundOrSuperseded
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DocumentTextMutationPlannerTest {
    val planner = DocumentTextMutationPlanner()

    val simpleCode = """
        f(1)

        g(2) {
            h(3)
            j(jj(4))
            k = 5
        }

        l(6)
    """.trimIndent()

    val simpleDocument = convertBlockToDocument(ParseTestUtil.parseAsTopLevelBlock(simpleCode))

    @Test
    fun `can plan removal of multiple nodes`() {
        val document = simpleDocument

        val plan = planner.planDocumentMutations(
            document, listOf(
                RemoveNode(document.content.toList()[0]),
                RemoveNode((document.content.toList()[1] as ElementNode).content.toList()[1])
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
        val topLevelElement = simpleDocument.content.toList()[1] as ElementNode
        val plan = planner.planDocumentMutations(
            simpleDocument, listOf(
                ElementNodeCallMutation(topLevelElement, CallMutation.RenameCall("g0")),
                RemoveNode(topLevelElement.content.toList()[1])
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
                ElementNodeCallMutation(simpleDocument.content.toList()[0] as ElementNode, CallMutation.RenameCall("f0")),
                RenamePropertyNode((simpleDocument.content.toList()[1] as ElementNode).content.toList()[2] as PropertyNode, "k0"),
                ValueNodeCallMutation((((simpleDocument.content.toList()[1] as ElementNode).content.toList()[1] as ElementNode).elementValues.single()) as ValueFactoryNode, CallMutation.RenameCall("jj0"))
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
        val blockNode = simpleDocument.content.toList()[1] as ElementNode
        val propertyNode = blockNode.content.toList()[2] as PropertyNode

        val mutations = listOf(
            RemoveNode(blockNode),
            RemoveNode(propertyNode),
            RenamePropertyNode(propertyNode, "k0")
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
    fun `rename superseded by another rename is reported`() {
        val propertyNode = (simpleDocument.content.toList()[1] as ElementNode).content.toList()[2] as PropertyNode

        val mutations = listOf(
            RenamePropertyNode(propertyNode, "k0"),
            RenamePropertyNode(propertyNode, "k1")
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

        assertTrue { plan.unsuccessfulDocumentMutations.single().let { it.mutation === mutations[0] && it.failureReasons.single() == TargetNotFoundOrSuperseded } }
    }
}
