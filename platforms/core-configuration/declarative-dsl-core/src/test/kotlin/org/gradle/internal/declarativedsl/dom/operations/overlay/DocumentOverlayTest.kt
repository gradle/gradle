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

package org.gradle.internal.declarativedsl.dom.operations.overlay

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.DomTestUtil
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlay.overlayResolvedDocuments
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals


internal
object DocumentOverlayTest {
    @Test
    fun `properties are combined in the result`() {
        val underlay = resolvedDocument(
            """
            x = 1
            y = 2
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            y = 3
            """.trimIndent()
        )

        overlayResolvedDocuments(underlay, overlay).document.assertMergeResult(
            """
            property(x, literal(1))
                literal(1)
            property(y, literal(3))
                literal(3)

            """.trimIndent(),
        )
    }

    @Test
    fun `underlay content of configuring block gets merged into the overlay one`() {
        val underlay = resolvedDocument(
            """
            configuring {
                a = 1
            }
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            configuring {
                b = myInt()
            }
            """.trimIndent()
        )

        overlayResolvedDocuments(underlay, overlay).document.assertMergeResult(
            """
            element(configuring, [], content.size = 2)
                property(a, literal(1))
                    literal(1)
                property(b, valueFactory(myInt, []))
                    valueFactory(myInt, [])

            """.trimIndent(),
        )
    }


    @Test
    fun `underlay adding blocks appear before overlay ones`() {
        val underlay = resolvedDocument(
            """
            adding(1) {
                a = 1
            }
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            adding(1) {
                b = 2
            }
            """.trimIndent()
        )

        overlayResolvedDocuments(underlay, overlay).document.assertMergeResult(
            """
            element(adding, [literal(1)], content.size = 1)
                literal(1)
                property(a, literal(1))
                    literal(1)
            element(adding, [literal(1)], content.size = 1)
                literal(1)
                property(b, literal(2))
                    literal(2)

            """.trimIndent(),
        )
    }


    @Test
    fun `configuring blocks get merged recursively`() {
        val underlay = resolvedDocument(
            """
            configuring {
                a = 1

                addingNested(1) {
                    a = 2
                }

                configuringNested {
                    a = 3

                    configuringNested {
                        a = 4
                    }
                }
            }
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            configuring {
                b = 5

                addingNested(1) {
                    b = 6
                }

                configuringNested {
                    b = 7

                    configuringNested {
                        b = 8
                    }
                }
            }
            """.trimIndent()
        )

        overlayResolvedDocuments(underlay, overlay).document.assertMergeResult(
            """
            element(configuring, [], content.size = 5)
                property(a, literal(1))
                    literal(1)
                element(addingNested, [literal(1)], content.size = 1)
                    literal(1)
                    property(a, literal(2))
                        literal(2)
                property(b, literal(5))
                    literal(5)
                element(addingNested, [literal(1)], content.size = 1)
                    literal(1)
                    property(b, literal(6))
                        literal(6)
                element(configuringNested, [], content.size = 3)
                    property(a, literal(3))
                        literal(3)
                    property(b, literal(7))
                        literal(7)
                    element(configuringNested, [], content.size = 2)
                        property(a, literal(4))
                            literal(4)
                        property(b, literal(8))
                            literal(8)

            """.trimIndent(),
        )
    }


    @Test
    fun `the overlay shows where the elements come from`() {
        val underlay = resolvedDocument(
            """
            x = 1
            adding(1) {
                a = 2
                unresolved1()
                errorExample(namedArgs = "are not supported")
            }
            configuring {
                a = 3
                unresolved2()
                !syntax!error!example!
            }
            unresolved3()
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            y = 4
            adding(1) {
                b = 5
                unresolved4()
            }
            configuring {
                a = 33
                b = 6
                unresolved5()
                errorExample = unsupported.propertyAccess
            }
            unresolved6()
            """.trimIndent()
        )

        val result = overlayResolvedDocuments(underlay, overlay)

        val overlayOriginDump = DomTestUtil.printDomByTraversal(
            result.document,
            { "* $it -> ${result.overlayNodeOriginContainer.data(it)}" },
            { "- $it -> ${result.overlayNodeOriginContainer.data(it)}" },
        )

        result.overlayNodeOriginContainer.collectToMap(result.document).entries.joinToString("\n") { "${it.key} -> ${it.value}" }

        assertEquals(
            """
            * property(x, literal(1)) -> FromUnderlay(documentNode=property(x, literal(1)))
                - literal(1) -> FromUnderlay(documentNode=property(x, literal(1)))
            * element(adding, [literal(1)], content.size = 3) -> FromUnderlay(documentNode=element(adding, [literal(1)], content.size = 3))
                - literal(1) -> FromUnderlay(documentNode=element(adding, [literal(1)], content.size = 3))
                * property(a, literal(2)) -> FromUnderlay(documentNode=property(a, literal(2)))
                    - literal(2) -> FromUnderlay(documentNode=property(a, literal(2)))
                * element(unresolved1, [], content.size = 0) -> FromUnderlay(documentNode=element(unresolved1, [], content.size = 0))
                * error(UnsupportedSyntax(cause=ElementArgumentFormat)) -> FromUnderlay(documentNode=error(UnsupportedSyntax(cause=ElementArgumentFormat)))
            * element(unresolved3, [], content.size = 0) -> FromUnderlay(documentNode=element(unresolved3, [], content.size = 0))
            * property(y, literal(4)) -> FromOverlay(documentNode=property(y, literal(4)))
                - literal(4) -> FromOverlay(documentNode=property(y, literal(4)))
            * element(adding, [literal(1)], content.size = 2) -> FromOverlay(documentNode=element(adding, [literal(1)], content.size = 2))
                - literal(1) -> FromOverlay(documentNode=element(adding, [literal(1)], content.size = 2))
                * property(b, literal(5)) -> FromOverlay(documentNode=property(b, literal(5)))
                    - literal(5) -> FromOverlay(documentNode=property(b, literal(5)))
                * element(unresolved4, [], content.size = 0) -> FromOverlay(documentNode=element(unresolved4, [], content.size = 0))
            * element(configuring, [], content.size = 7) -> MergedElements(underlayElement=element(configuring, [], content.size = 4), overlayElement=element(configuring, [], content.size = 4))
                * element(unresolved2, [], content.size = 0) -> FromUnderlay(documentNode=element(unresolved2, [], content.size = 0))
                * error(UnsupportedKotlinFeature(unsupportedConstruct=UnsupportedConstruct(potentialElementSource=LightTreeSourceData(test:164..170), erroneousSource=LightTreeSourceData(test:164..170), languageFeature=PrefixExpression))) -> FromUnderlay(documentNode=error(UnsupportedKotlinFeature(unsupportedConstruct=UnsupportedConstruct(potentialElementSource=LightTreeSourceData(test:164..170), erroneousSource=LightTreeSourceData(test:164..170), languageFeature=PrefixExpression))))
                * error(SyntaxError(parsingError=ParsingError(potentialElementSource=LightTreeSourceData(test:171..185), erroneousSource=LightTreeSourceData(test:171..185), message=Unexpected tokens (use ';' to separate expressions on the same line)))) -> FromUnderlay(documentNode=error(SyntaxError(parsingError=ParsingError(potentialElementSource=LightTreeSourceData(test:171..185), erroneousSource=LightTreeSourceData(test:171..185), message=Unexpected tokens (use ';' to separate expressions on the same line)))))
                * property(a, literal(33)) -> ShadowedProperty(underlayProperty=property(a, literal(3)), overlayProperty=property(a, literal(33)))
                    - literal(33) -> FromOverlay(documentNode=property(a, literal(33)))
                * property(b, literal(6)) -> FromOverlay(documentNode=property(b, literal(6)))
                    - literal(6) -> FromOverlay(documentNode=property(b, literal(6)))
                * element(unresolved5, [], content.size = 0) -> FromOverlay(documentNode=element(unresolved5, [], content.size = 0))
                * error(UnsupportedSyntax(cause=UnsupportedPropertyAccess)) -> FromOverlay(documentNode=error(UnsupportedSyntax(cause=UnsupportedPropertyAccess)))
            * element(unresolved6, [], content.size = 0) -> FromOverlay(documentNode=element(unresolved6, [], content.size = 0))

            """.trimIndent(),
            overlayOriginDump
        )
    }


    @Test
    fun `the overlay result can be resolved with the merged container`() {
        val underlay = resolvedDocument(
            """
            x = 1
            adding(1) {
                a = 2
                unresolved1()
            }
            configuring {
                a = 3
                unresolved2()
            }
            unresolved3()
            """.trimIndent()
        )

        val overlay = resolvedDocument(
            """
            y = 4
            adding(1) {
                b = myInt()
                unresolved4()
            }
            configuring {
                b = myInt()
                unresolved5()
            }
            unresolved6()
            """.trimIndent()
        )

        val result = overlayResolvedDocuments(underlay, overlay)

        val resolutionDump = dumpDocumentWithResolution(DocumentWithResolution(result.document, result.overlayResolutionContainer))

        assertEquals(
            """
            * property(x, literal(1)) -> property(Int)
                - literal(1) -> literal
            * element(adding, [literal(1)], content.size = 2) -> element(NestedReceiver)
                - literal(1) -> literal
                * property(a, literal(2)) -> property(Int)
                    - literal(2) -> literal
                * element(unresolved1, [], content.size = 0) -> notResolved(UnresolvedSignature)
            * element(unresolved3, [], content.size = 0) -> notResolved(UnresolvedSignature)
            * property(y, literal(4)) -> property(Int)
                - literal(4) -> literal
            * element(adding, [literal(1)], content.size = 2) -> element(NestedReceiver)
                - literal(1) -> literal
                * property(b, valueFactory(myInt, [])) -> property(Int)
                    - valueFactory(myInt, []) -> valueFactory
                * element(unresolved4, [], content.size = 0) -> notResolved(UnresolvedSignature)
            * element(configuring, [], content.size = 4) -> configuring(NestedReceiver)
                * property(a, literal(3)) -> property(Int)
                    - literal(3) -> literal
                * element(unresolved2, [], content.size = 0) -> notResolved(UnresolvedSignature)
                * property(b, valueFactory(myInt, [])) -> property(Int)
                    - valueFactory(myInt, []) -> valueFactory
                * element(unresolved5, [], content.size = 0) -> notResolved(UnresolvedSignature)
            * element(unresolved6, [], content.size = 0) -> notResolved(UnresolvedSignature)

            """.trimIndent(),
            resolutionDump
        )
    }

    @Test
    fun `can use the merge result as an input`() {
        val docs = listOf(
            resolvedDocument("x = 1"),
            resolvedDocument("y = 2"),
            resolvedDocument("configuring { a = 3 }"),
            resolvedDocument("configuring { b = 4 }")
        )

        val result = docs.reduce { acc, it ->
            val overlayResult = overlayResolvedDocuments(acc, it)
            DocumentWithResolution(overlayResult.document, overlayResult.overlayResolutionContainer)
        }

        assertEquals(
            """
            * property(x, literal(1)) -> property(Int)
                - literal(1) -> literal
            * property(y, literal(2)) -> property(Int)
                - literal(2) -> literal
            * element(configuring, [], content.size = 2) -> configuring(NestedReceiver)
                * property(a, literal(3)) -> property(Int)
                    - literal(3) -> literal
                * property(b, literal(4)) -> property(Int)
                    - literal(4) -> literal

            """.trimIndent(),
            dumpDocumentWithResolution(result)
        )
    }

    private
    fun dumpDocumentWithResolution(documentWithResolution: DocumentWithResolution) =
        DomTestUtil.printDomByTraversal(
            documentWithResolution.document,
            { "* $it -> ${prettyPrintResolution(documentWithResolution.resolutionContainer.data(it))}" },
            { "- $it -> ${prettyPrintResolution(documentWithResolution.resolutionContainer.data(it))}" },
        )

    private
    val schema = schemaFromTypes(TopLevelReceiver::class, listOf(TopLevelReceiver::class, NestedReceiver::class))

    private
    fun DeclarativeDocument.assertMergeResult(expectedDomContent: String) {
        assertEquals(expectedDomContent, DomTestUtil.printDomByTraversal(this, Any::toString, Any::toString))
    }

    private
    fun resolvedDocument(code: String) =
        documentWithResolution(schema, ParseTestUtil.parse(code), DefaultOperationGenerationId.finalEvaluation, analyzeEverything)

    interface TopLevelReceiver {
        @get:Restricted
        var x: Int

        @get: Restricted
        var y: Int

        @Configuring
        fun configuring(configure: NestedReceiver.() -> Unit)

        @Adding
        fun adding(someValue: Int, configure: NestedReceiver.() -> Unit): NestedReceiver

        @Restricted
        fun myInt(): Int
    }

    interface NestedReceiver {
        @get:Restricted
        var a: Int

        @get:Restricted
        var b: Int

        @Configuring
        fun configuringNested(nestedReceiver: NestedReceiver.() -> Unit)

        @Adding
        fun addingNested(someValue: Int, nestedReceiver: NestedReceiver.() -> Unit): NestedReceiver
    }

    private
    fun prettyPrintResolution(documentResolution: DocumentResolution): String = when (documentResolution) {
        is DocumentResolution.ElementResolution.ElementNotResolved -> "notResolved(${documentResolution.reasons.joinToString()})"
        DocumentResolution.ErrorResolution -> "errorResolution"
        is DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved -> "configuring(${documentResolution.elementType})"
        is DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved -> "element(${documentResolution.elementType})"
        is DocumentResolution.PropertyResolution.PropertyAssignmentResolved -> "property(${documentResolution.property.valueType})"
        is DocumentResolution.PropertyResolution.PropertyNotAssigned -> "notAssigned(${documentResolution.reasons.joinToString()})"
        is DocumentResolution.ValueNodeResolution.LiteralValueResolved -> "literal"
        is DocumentResolution.ValueNodeResolution.ValueFactoryResolution.ValueFactoryResolved -> "valueFactory"
        is DocumentResolution.ValueNodeResolution.ValueFactoryResolution.ValueFactoryNotResolved -> "valueFactoryNotResolved"
    }
}
