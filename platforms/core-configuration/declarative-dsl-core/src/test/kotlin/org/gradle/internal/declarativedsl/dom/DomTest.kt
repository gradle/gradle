/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.parseAsTopLevelBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class DomTest {
    @Test
    fun `converts a simple language tree to document`() {
        val tree = parseAsTopLevelBlock(
            """
            myFun {
                a = 1
                b = f("x", z.f("y"))
                c = true
                nested {
                    x = "y"
                }
                factory(1)
            }
            """.trimIndent()
        )

        assertEquals(
            """
            element(myFun)[0..106]
                property(a, literal(1)[16..16])[12..16]
                property(b, valueFactory(f, literal(x)[28..30], valueFactory(z.f, literal(y)[37..39])[35..40])[26..41])[22..41]
                property(c, literal(true)[51..54])[47..54]
                element(nested)[60..89]
                    property(x, literal(y)[81..83])[77..83]
                element(factory, literal(1)[103..103])[95..104]

            """.trimIndent(),
            DomPrettyPrinter(withSourceData = true).domAsString(convertBlockToDocument(tree))
        )
    }

    @Test
    fun `declarative document has the same source identifier as its nodes`() {
        val tree = parseAsTopLevelBlock(
            """
            a = 1
            """.trimIndent()
        )

        val document = convertBlockToDocument(tree)

        assertEquals("test", document.sourceIdentifier.fileIdentifier)
        assertEquals(document.sourceIdentifier, document.content.single().sourceData.sourceIdentifier)
    }

    @Test
    fun `converts unsupported syntax to errors`() {
        val tree = parseAsTopLevelBlock(
            """
            myFun {
                a = "x"
                a = b
                b = f(a = "x")
                b = f(a = "x") { test() }
                call(x = { }) // TODO: right now, it is reported as an unsupported language feature FunctionDeclaration, report it in a more precise way?
                multiLambda({ }, { })
                1
                a.b()
                a.x = 1
                val x = 1
                syntaxError = syntaxError!!!
                a = this
                a = null
                factory(1)
            }
            """.trimIndent()
        )

        assertEquals(
            """
            element(myFun)[0..363]
                property(a, literal(x)[16..18])[12..18]
                error(UnsupportedSyntax(UnsupportedPropertyAccess)[24..28]
                error(UnsupportedSyntax(ValueFactoryArgumentFormat)[34..47]
                error(UnsupportedSyntax(ValueFactoryArgumentFormat)[53..77]
                error(UnsupportedKotlinFeature(FunctionDeclaration)[92..94]
                error(UnsupportedKotlinFeature(FunctionDeclaration), UnsupportedKotlinFeature(FunctionDeclaration)[237..239]
                error(UnsupportedSyntax(DanglingExpr)[251..251]
                error(UnsupportedSyntax(ElementWithExplicitReceiver)[259..261]
                error(UnsupportedSyntax(AssignmentWithExplicitReceiver)[267..273]
                error(UnsupportedSyntax(LocalVal)[279..287]
                error(SyntaxError(Parsing failure, unexpected tokenType in expression: POSTFIX_EXPRESSION)[307..319]
                error(SyntaxError(Unexpected tokens (use ';' to separate expressions on the same line))[320..320]
                error(UnsupportedSyntax(UnsupportedThisValue)[326..333]
                error(UnsupportedSyntax(UnsupportedNullValue)[339..346]
                element(factory, literal(1)[360..360])[352..361]

            """.trimIndent(),

            DomPrettyPrinter(withSourceData = true).domAsString(convertBlockToDocument(tree))
        )
    }

    internal
    class DomPrettyPrinter(private val withSourceData: Boolean) {
        fun domAsString(document: DeclarativeDocument) = buildString {
            fun valueToString(valueNode: DeclarativeDocument.ValueNode): String = when (valueNode) {
                is DeclarativeDocument.ValueNode.ValueFactoryNode -> "valueFactory(${valueNode.factoryName}" +
                    (if (valueNode.values.isNotEmpty()) ", ${valueNode.values.joinToString { valueToString(it) }})" else ")") +
                    maybeSourceData(valueNode)

                is DeclarativeDocument.ValueNode.LiteralValueNode -> "literal(${valueNode.value})" + maybeSourceData(valueNode)
            }

            fun visit(node: DeclarativeDocument.DocumentNode, depth: Int = 0) {
                append("    ".repeat(depth))
                when (node) {
                    is DeclarativeDocument.DocumentNode.ElementNode -> {
                        appendLine(
                            "element(${node.name}" +
                                (if (node.elementValues.isEmpty()) ")" else ", ${node.elementValues.joinToString { valueToString(it) }})") +
                                maybeSourceData(node)
                        )
                        node.content.forEach { visit(it, depth + 1) }
                    }

                    is DeclarativeDocument.DocumentNode.PropertyNode -> appendLine("property(${node.name}, ${valueToString(node.value)})" + maybeSourceData(node))
                    is DeclarativeDocument.DocumentNode.ErrorNode -> appendLine("error(${node.errors.joinToString { errorString(it) }}" + maybeSourceData(node))
                }
            }

            document.content.forEach(::visit)
        }

        private
        fun errorString(error: DocumentError): String = when (error) {
            is SyntaxError -> "SyntaxError(${error.parsingError.message})"
            is UnsupportedKotlinFeature -> "UnsupportedKotlinFeature(${error.unsupportedConstruct.languageFeature})"
            is UnsupportedSyntax -> "UnsupportedSyntax(${error.cause})"
        }

        private
        fun maybeSourceData(documentNode: DeclarativeDocument.DocumentNode) =
            if (withSourceData) sourceDataString(documentNode.sourceData) else ""

        private
        fun maybeSourceData(valueNode: DeclarativeDocument.ValueNode) =
            if (withSourceData) sourceDataString(valueNode.sourceData) else ""

        private
        fun sourceDataString(sourceData: SourceData) = "[${sourceData.indexRange}]"
    }
}
