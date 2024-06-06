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
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InAllNestedScopes
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InNestedScopes
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Test
import kotlin.test.assertEquals


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

        val topLevelBlock = ParseTestUtil.parseAsTopLevelBlock(
            """
                a { // resolved to #a
                    b { // resolved to a#b
                        c { } // resolved to a#b#c
                    }
                }
            """.trimIndent())

        val document = convertBlockToDocument(topLevelBlock)

        val resolver = tracingCodeResolver()
        resolver.resolve(schema, emptyList(), topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document)

        val elementResolutions: Map<String, ElementResolution> = toStringKeyedElementResolutions(document, resolved)
        val elementA = elementResolutions["a"]!!
        val elementB = elementResolutions["b"]!!
        val elementC = elementResolutions["c"]!!

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InAllNestedScopes
                )
            ),
            document,
            resolved,
            setOf(
                Scope.topLevel(),
                Scope.nestedBlocks(elementA),
                Scope.nestedBlocks(elementA, elementB),
                Scope.nestedBlocks(elementA, elementB, elementC)
            )
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("A")))
                )
            ),
            document,
            resolved,
            setOf(Scope.nestedBlocks(elementA))
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("B")))
                )
            ),
            document,
            resolved,
            setOf()
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("B"))),
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("C")))
                )
            ),
            document,
            resolved,
            setOf()
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InAllNestedScopes,
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("B"))),
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("C")))
                )
            ),
            document,
            resolved,
            setOf(Scope.nestedBlocks(elementA, elementB, elementC))
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("A"))),
                    InAllNestedScopes,
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("C")))
                )
            ),
            document,
            resolved,
            setOf(Scope.nestedBlocks(elementA, elementB, elementC))
        )

        assertScopeLocationMatch(
            ScopeLocation(
                listOf(
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("A"))),
                    InNestedScopes(NestedObjectsOfType(schema.dataClass("B")))
                )
            ),
            document,
            resolved,
            setOf(Scope.nestedBlocks(elementA, elementB))
        )
    }

    private
    fun toStringKeyedElementResolutions(document: DeclarativeDocument, resolution: DocumentResolutionContainer): Map<String, ElementResolution> =
        document.elementNodes().associate { it.name to (it to resolution.data(it)) }

    private
    fun assertScopeLocationMatch(
        scopeLocation: ScopeLocation,
        document: DeclarativeDocument,
        resolution: DocumentResolutionContainer,
        expectedScopes: Set<Scope>
    ) {
        assertEquals(
            expectedScopes,
            scopeLocation.match(document, resolution)
        )
    }

    private
    fun AnalysisSchema.dataClass(description: String): DataClass {
        return dataClassesByFqName.entries
            .first {
                it.key.simpleName == description
            }.value
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
