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

package org.gradle.internal.declarativedsl

import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Element
import org.gradle.internal.declarativedsl.language.ErroneousStatement
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Import
import org.gradle.internal.declarativedsl.language.LanguageResult
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.MultipleFailuresResult
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.This
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil.Parser.parse


fun prettyPrintLanguageTreeResult(languageTreeResult: LanguageTreeResult): String {
    val languageResults = languageTreeResult.imports.map { Element(it) } + languageTreeResult.topLevelBlock.content.map { Element(it) }
    return languageResults.joinToString("\n") { prettyPrintLanguageResult(it) }
}


fun prettyPrintLanguageResult(languageResult: LanguageResult<*>, startDepth: Int = 0): String {
    fun StringBuilder.recurse(current: LanguageResult<*>, depth: Int) {
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
        fun recurseDeeper(next: LanguageResult<*>) = recurse(next, depth + 1)
        when (current) {
            is MultipleFailuresResult -> {
                appendIndented("MultipleFailures(\n")
                current.failures.forEach {
                    recurseDeeper(it)
                    appendLine()
                }
                appendIndented(")")
            }
            is ParsingError -> {
                appendIndented("ParsingError(")
                appendLine()
                appendNextIndented("message = ${current.message},")
                appendLine()
                appendNextIndented("potentialElementSource = ${current.potentialElementSource.prettyPrint()},")
                appendLine()
                appendNextIndented("erroneousSource = ${current.erroneousSource.prettyPrint()}")
                appendLine()
                appendIndented(")")
            }
            is UnsupportedConstruct -> {
                appendIndented("UnsupportedConstruct(")
                appendLine()
                appendNextIndented("languageFeature = ${current.languageFeature.javaClass.simpleName},")
                appendLine()
                appendNextIndented("potentialElementSource = ${current.potentialElementSource.prettyPrint()},")
                appendLine()
                appendNextIndented("erroneousSource = ${current.erroneousSource.prettyPrint()}")
                appendLine()
                appendIndented(")")
            }
            is Element -> {
                append(prettyPrintLanguageTree(current.element as LanguageTreeElement))
            }
            else -> { error("Unhandled language result type: ${current.javaClass.simpleName}") }
        }
    }
    return buildString { recurse(languageResult, startDepth) }
}


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

        fun source() = current.sourceData.prettyPrint()

        when (current) {
            is Block -> {
                append("Block [${source()}] (\n")
                current.content.forEach {
                    append(nextIndent())
                    recurseDeeper(it)
                    appendLine()
                }
                appendIndented(")")
            }

            is Assignment -> {
                append("Assignment [${source()}] (\n")
                appendNextIndented("lhs = ")
                recurseDeeper(current.lhs)
                appendLine()
                appendNextIndented("rhs = ")
                recurseDeeper(current.rhs)
                appendLine()
                appendIndented(")")
            }

            is FunctionCall -> {
                append("FunctionCall [${source()}] (\n")
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
                append("BooleanLiteral [${source()}] (${current.value})")
            }

            is Literal.IntLiteral -> {
                append("IntLiteral [${source()}] (${current.value})")
            }

            is Literal.LongLiteral -> {
                append("LongLiteral [${source()}] (${current.value})")
            }

            is Literal.StringLiteral -> {
                append("StringLiteral [${source()}] (${current.value})")
            }

            is Null -> append("Null")
            is PropertyAccess -> {
                append("PropertyAccess [${source()}] (\n")
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
                append("LocalValue [${source()}] (\n")
                appendNextIndented("name = ${current.name}\n")
                appendNextIndented("rhs = ")
                recurseDeeper(current.rhs)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Lambda -> {
                append("FunctionArgument.Lambda [${source()}] (\n")
                appendNextIndented("block = ")
                recurseDeeper(current.block)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Named -> {
                append("FunctionArgument.Named [${source()}] (\n")
                appendNextIndented("name = ${current.name},\n")
                appendNextIndented("expr = ")
                recurseDeeper(current.expr)
                appendLine()
                appendIndented(")")
            }

            is FunctionArgument.Positional -> {
                append("FunctionArgument.Positional [${source()}] (\n")
                appendNextIndented("expr = ")
                recurseDeeper(current.expr)
                appendLine()
                appendIndented(")")
            }

            is Import -> {
                append("Import [${source()} (\n")
                appendNextIndented("name parts = ${current.name.nameParts}")
                appendLine()
                appendIndented(")")
            }

            is ErroneousStatement -> {
                append("ErroneousStatement (\n")
                append(prettyPrintLanguageResult(current.failingResult, depth + 1))
                appendLine()
                appendIndented(")")
            }
        }
    }

    return buildString { recurse(languageTreeElement, 0) }
}


private
fun SourceData.prettyPrint(): String =
    buildString {
        // We add +1 here as that was how the original implementation worked, just to avoid fixing all test data:
        append("indexes: ${indexRange.start}..${indexRange.endInclusive + 1}, ")
        append("line/column: ${lineRange.first}/$startColumn..${lineRange.last}/${endColumn + 1}, ")
        append("file: ${sourceIdentifier.fileIdentifier}")
    }


fun main() {
    val result = parse(
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
                google() }
        }
        """.trimIndent()
    ).topLevelBlock

    println(prettyPrintLanguageTree(result))
}
