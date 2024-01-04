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

package com.example.com.h0tk3y.kotlin.staticObjectNotation.dom

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.DefaultLanguageTreeBuilder
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.Element
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ElementResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeBuilderWithTopLevelBlock
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.parseToLightTree
import com.h0tk3y.kotlin.staticObjectNotation.dom.DeclarativeDocument
import com.h0tk3y.kotlin.staticObjectNotation.dom.convertBlockToDocument
import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import org.intellij.lang.annotations.Language
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

object DomTest {
    @Test
    fun `converts a simple language tree to document`() {
        val tree = parseWithTopLevelBlock(
            """
            myFun {
                a = 1
                b = f("x", a.f("y"))
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
            element(myFun)
                property(a, literal(1))
                property(b, valueFactory(f, literal(x), valueFactory(a.f, literal(y))))
                property(c, literal(true))
                element(nested)
                    property(x, literal(y))
                element(factory, literal(1))

            """.trimIndent(),
            domAsString(convertBlockToDocument((tree.single() as Element).element as Block))
        )
    }

    @Test
    fun `converts unsupported syntax to errors`() {
        val tree = parseWithTopLevelBlock(
            """
            myFun {
                a = "x"
                a = b
                b = f(a = "x")
                b = f(a = "x") { test() }
                // call(x = { }) -- todo
                // multiLambda({ }, { }) -- todo
                1
                a.b()
                a.x = 1
                val x = 1
                // a = this -- todo
                // a = null -- todo
                factory(1)
            }
            """.trimIndent()
        )

        assertEquals(
            """
                element(myFun)
                    property(a, literal(x))
                    error(UnsupportedSyntax(cause=UnsupportedPropertyAccess))
                    error(UnsupportedSyntax(cause=ValueFactoryArgumentFormat))
                    error(UnsupportedSyntax(cause=ValueFactoryArgumentFormat))
                    error(UnsupportedSyntax(cause=DanglingExpr))
                    error(UnsupportedSyntax(cause=ElementWithExplicitReceiver))
                    error(UnsupportedSyntax(cause=AssignmentWithExplicitReceiver))
                    error(UnsupportedSyntax(cause=LocalVal))
                    element(factory, literal(1))

            """.trimIndent(),
            domAsString(convertBlockToDocument((tree.single() as Element).element as Block))
        )
    }

    @Test
    @Ignore
    fun `converts unsupported language features to errors`() {
        // TODO
    }

    @Test
    @Ignore
    fun `converts syntax errors to errors in the document`() {
        // TODO
    }

    private fun parseWithTopLevelBlock(@Language("kts") code: String): List<ElementResult<*>> {
        val (tree, sourceCode, sourceOffset) = parseToLightTree(code)
        return LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder()).build(tree, sourceCode, sourceOffset, SourceIdentifier("test")).results
    }

    private fun domAsString(document: DeclarativeDocument) = buildString {
        fun valueToString(valueNode: DeclarativeDocument.ValueNode): String = when (valueNode) {
            is DeclarativeDocument.ValueNode.ValueFactoryNode -> "valueFactory(${valueNode.factoryName}" +
                if (valueNode.values.isNotEmpty()) ", ${valueNode.values.joinToString { valueToString(it) }})" else ")"

            is DeclarativeDocument.ValueNode.LiteralValueNode -> "literal(${valueNode.value})"
        }

        fun visit(node: DeclarativeDocument.DocumentNode, depth: Int = 0) {
            append("    ".repeat(depth))
            when (node) {
                is DeclarativeDocument.DocumentNode.ElementNode -> {
                    appendLine(
                        "element(${node.name}" +
                            if (node.elementValues.isEmpty()) ")" else ", ${node.elementValues.joinToString { valueToString(it) }})"
                    )
                    node.content.forEach { visit(it, depth + 1) }
                }

                is DeclarativeDocument.DocumentNode.PropertyNode -> appendLine("property(${node.name}, ${valueToString(node.value)})")
                is DeclarativeDocument.DocumentNode.ErrorNode -> appendLine("error(${node.errors.joinToString()})")
            }
        }

        document.content.forEach(::visit)
    }
}
