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

import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.intellij.lang.annotations.Language
import kotlin.test.Test
import kotlin.test.assertEquals


object DomTest {
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
            element(myFun)[0..107]
                property(a, literal(1)[16..17])[12..17]
                property(b, valueFactory(f, literal(x)[28..31], valueFactory(z.f, literal(y)[37..40])[35..41])[26..42])[22..42]
                property(c, literal(true)[51..55])[47..55]
                element(nested)[60..90]
                    property(x, literal(y)[81..84])[77..84]
                element(factory, literal(1)[103..104])[95..105]

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
            element(myFun)[0..364]
                property(a, literal(x)[16..19])[12..19]
                error(UnsupportedSyntax(UnsupportedPropertyAccess)[24..29]
                error(UnsupportedSyntax(ValueFactoryArgumentFormat)[34..48]
                error(UnsupportedSyntax(ValueFactoryArgumentFormat)[53..78]
                error(UnsupportedKotlinFeature(FunctionDeclaration)[92..95]
                error(UnsupportedKotlinFeature(FunctionDeclaration), UnsupportedKotlinFeature(FunctionDeclaration)[237..240]
                error(UnsupportedSyntax(DanglingExpr)[251..252]
                error(UnsupportedSyntax(ElementWithExplicitReceiver)[259..262]
                error(UnsupportedSyntax(AssignmentWithExplicitReceiver)[267..274]
                error(UnsupportedSyntax(LocalVal)[279..288]
                error(SyntaxError(Parsing failure, unexpected tokenType in expression: POSTFIX_EXPRESSION)[307..320]
                error(SyntaxError(Unexpected tokens (use ';' to separate expressions on the same line))[320..321]
                error(UnsupportedSyntax(UnsupportedThisValue)[326..334]
                error(UnsupportedSyntax(UnsupportedNullValue)[339..347]
                element(factory, literal(1)[360..361])[352..362]

            """.trimIndent(),

            DomPrettyPrinter(withSourceData = true).domAsString(convertBlockToDocument(tree))
        )
    }

    private
    fun parseAsTopLevelBlock(@Language("kts") code: String): Block {
        val (tree, sourceCode, sourceOffset) = parse(code)
        return DefaultLanguageTreeBuilder().build(tree, sourceCode, sourceOffset, SourceIdentifier("test")).topLevelBlock
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
