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

package com.example.com.h0tk3y.kotlin.staticObjectNotation

import com.h0tk3y.kotlin.staticObjectNotation.language.Assignment
import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionArgument
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.language.Import
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.language.Literal
import com.h0tk3y.kotlin.staticObjectNotation.language.LocalValue
import com.h0tk3y.kotlin.staticObjectNotation.language.Null
import com.h0tk3y.kotlin.staticObjectNotation.language.PropertyAccess
import com.h0tk3y.kotlin.staticObjectNotation.language.This

fun prettyPrintLanguageTree(languageTreeElement: LanguageTreeElement): String {
    fun StringBuilder.recurse(current: LanguageTreeElement, depth: Int) {
        fun indent() = "    ".repeat(depth)
        fun nextIndent() = "    ".repeat(depth + 1)
        fun appendIndented(value: Any) {
            append(indent())
            append(value)
        }

        fun appendNextIndented(value: Any) {
            append(nextIndent())
            append(value)
        }

        fun recurseDeeper(next: LanguageTreeElement) = recurse(next, depth + 1)

        when (current) {
            is Block -> {
                append("Block(\n")
                current.statements.forEach {
                    append(nextIndent())
                    recurseDeeper(it)
                    appendLine()
                }
                appendIndented(")")
            }

            is Assignment -> {
                append("Assignment(\n")
                appendNextIndented("lhs = ")
                recurseDeeper(current.lhs)
                appendLine()
                appendNextIndented("rhs = ")
                recurseDeeper(current.rhs)
                appendLine()
                appendIndented(")")
            }

            is FunctionCall -> {
                append("FunctionCall(\n")
                appendNextIndented("name = ${current.name}\n")
                current.receiver?.let {
                    appendNextIndented("receiver = ")
                    recurseDeeper(it)
                    appendLine()
                }
                if (current.args.isNotEmpty()) {
                    appendNextIndented("args = [\n")
                    current.args.forEach {
                        appendNextIndented("    ")
                        recurse(it, depth + 2)
                        appendLine()
                    }
                    appendNextIndented("]\n")
                } else {
                    appendNextIndented("args = []\n")
                }
                appendIndented(")")
            }

            is Literal.BooleanLiteral -> {
                append("BooleanLiteral(${current.value})")
            }

            is Literal.IntLiteral -> {
                append("IntLiteral(${current.value})")
            }

            is Literal.LongLiteral -> {
                append("LongLiteral(${current.value})")
            }

            is Literal.StringLiteral -> {
                append("StringLiteral(${current.value})")
            }

            is Null -> append("Null")
            is PropertyAccess -> {
                append("PropertyAccess(\n")
                current.receiver?.let { receiver ->
                    appendNextIndented("receiver = ")
                    recurseDeeper(receiver)
                    appendLine()
                }
                appendNextIndented("name = ${current.name}\n")
                appendIndented(")")
            }

            is This -> append("This")

            is LocalValue -> {
                append("LocalValue(\n")
                appendNextIndented("name = ${current.name}\n")
                appendNextIndented("rhs = ")
                recurseDeeper(current.rhs)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Lambda -> {
                append("FunctionArgument.Lambda(\n")
                appendNextIndented("block = ")
                recurseDeeper(current.block)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Named -> {
                append("FunctionArgument.Named(\n")
                appendNextIndented("name = ${current.name},\n")
                appendNextIndented("expr = ")
                recurseDeeper(current.expr)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Positional -> {
                append("FunctionArgument.Positional(\n")
                appendNextIndented("expr = ")
                recurseDeeper(current.expr)
                appendLine()
                appendIndented(")")
            }

            is Import -> TODO()
        }
    }

    return buildString { recurse(languageTreeElement, 0) }
}

fun main() {
    /*val result = parseWithTopLevelBlock(
        """
        rootProject.name = "test-value"
        include(":a")
        include(projectPath = ":b")

        dependencyResolutionManagement {
            repositories {
                mavenCentral()
                google()
            }
        }
        pluginManagement {
            includeBuild("pluginIncluded")
            repositories {
                mavenCentral()
                google()
            }
        }
        """.trimIndent()
    ).single() as Element<*>

    println(prettyPrintLanguageTree(result.element))*/
}
