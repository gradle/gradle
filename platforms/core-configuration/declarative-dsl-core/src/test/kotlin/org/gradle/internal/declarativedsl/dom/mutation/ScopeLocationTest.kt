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

import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationTest.TestApiAbc.A
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationTest.TestApiAbc.B
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationTest.TestApiAbc.C
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class ScopeLocationTest {

    /* todo: test for
        b { // resolved to Foo#b
           c { } // resolved to Bar#c function
        }
        c { // resolved to Bar#c function
           d { }
        }
     */

    @Test
    fun `deeply nested blocks`() {
        val schema = schemaFromTypes(TestApiAbc.TopLevelReceiver::class, TestApiAbc::class.nestedClasses.toList())

        val topLevelBlock = ParseTestUtil.parse(
            """
                a { // resolved to #a
                    b { // resolved to a#b
                        c { } // resolved to a#b#c
                    }
                }
            """.trimIndent())

        val resolved = documentWithResolution(schema, topLevelBlock)
        val documentMemberMatcher = DocumentMemberAndTypeMatcher(schema, resolved.resolutionContainer)

        val elementResolutions: Map<String, ElementNode> = toStringKeyedElements(resolved.document)
        val elementA = elementResolutions["a"]!!
        val elementB = elementResolutions["b"]!!
        val elementC = elementResolutions["c"]!!

        val locationMatcher = ScopeLocationMatcher(schema.topLevelReceiverType, resolved, documentMemberMatcher)

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.inAnyScope(),
            setOf(
                Scope.topLevel(),
                Scope(listOf(elementA)),
                nestedBlocks(elementA, elementB),
                nestedBlocks(elementA, elementB, elementC)
            )
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<A>()),
            setOf(nestedBlocks(elementA))
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<B>()),
            setOf()
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<B>()).inObjectsOfType(schema.typeFor<C>()),
            setOf()
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.inAnyScope().inObjectsOfType(schema.typeFor<B>()).inObjectsOfType(schema.typeFor<C>()),
            setOf(nestedBlocks(elementA, elementB, elementC))
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<A>()).alsoInNestedScopes().inObjectsOfType(schema.typeFor<C>()),
            setOf(nestedBlocks(elementA, elementB, elementC))
        )

        assertScopeLocationMatch(
            locationMatcher,
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<A>()).inObjectsOfType(schema.typeFor<B>()),
            setOf(nestedBlocks(elementA, elementB))
        )
    }

    private
    fun toStringKeyedElements(document: DeclarativeDocument): Map<String, ElementNode> =
        document.elementNodes().associate { it.name to it }

    private
    fun assertScopeLocationMatch(
        locationMatcher: ScopeLocationMatcher,
        scopeLocation: ScopeLocation,
        expectedScopes: Set<Scope>
    ) {
        assertEquals(
            expectedScopes,
            locationMatcher.match(scopeLocation)
        )
    }

    private
    fun DeclarativeDocument.elementNodes(): Set<ElementNode> {
        val nodes = mutableSetOf<ElementNode>()

        fun visitNode(node: DocumentNode) {
            when (node) {
                is ElementNode -> {
                    nodes.add(node)
                    node.content.forEach {
                        visitNode(it)
                    }
                }
                else -> Unit
            }
        }

        content.forEach(::visitNode)

        return nodes
    }

    private
    fun nestedBlocks(vararg elementNodes: ElementNode): Scope =
        Scope(elementNodes.toList())

    class TestApiAbc {
        class TopLevelReceiver {

            @Configuring
            fun a(configure: A.() -> Unit) = configure(a)

            @get:Restricted
            @get:HiddenInDeclarativeDsl
            val a = A()
        }

        class A {
            @Configuring
            fun b(configure: B.() -> Unit) = configure(b)

            @get:Restricted
            @get:HiddenInDeclarativeDsl
            val b = B()
        }

        class B {
            @Configuring
            fun c(configure: C.() -> Unit) = configure(c)

            @get:Restricted
            @get:HiddenInDeclarativeDsl
            val c = C()
        }

        class C
    }
}
