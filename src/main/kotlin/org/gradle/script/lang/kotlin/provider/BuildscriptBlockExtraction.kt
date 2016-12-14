/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.provider

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens.*

class UnexpectedBlockException(val blockIdentifier: String, val blockLocation: IntRange)
    : RuntimeException("Unexpected block found.")

fun extractBuildscriptBlockFrom(script: String) =
    extractTopLevelSectionFrom(script, "buildscript")

/**
 * Extract a top-level section from the given [script]. The section must be in the form:
 *
 *     [identifier] { anything* }
 *
 * @return range of found section or null if no top-level section with the given [identifier] could be found
 * @throws UnexpectedBlockException if more than one top-level section with the given [identifier] is found
 */
fun extractTopLevelSectionFrom(script: String, identifier: String): IntRange? =
    KotlinLexer().run {
        start(script)
        while (tokenType != null) {
            nextTopLevelSection(identifier)?.let {
                advance()
                expectNoMore(identifier)
                return it
            }
        }
        return null
    }

private fun KotlinLexer.expectNoMore(identifier: String) {
    nextTopLevelSection(identifier)?.let {
        throw UnexpectedBlockException(identifier, it)
    }
}

private fun KotlinLexer.nextTopLevelSection(identifier: String): IntRange? =
    findTopLevelIdentifier(identifier)?.let { sectionStart ->
        advance()
        skipWhiteSpaceAndComments()
        if (tokenType == LBRACE) {
            findBlockEnd()?.let { sectionEnd ->
                sectionStart..sectionEnd
            }
        } else null
    }

private fun KotlinLexer.skipWhiteSpaceAndComments() {
    while (tokenType in WHITE_SPACE_OR_COMMENT_BIT_SET) {
        advance()
    }
}

private fun KotlinLexer.findTopLevelIdentifier(identifier: String): Int? {
    var depth: Int = 0
    while (tokenType != null) {
        when (tokenType) {
            IDENTIFIER ->
                if (depth == 0 && tokenText == identifier) {
                    return tokenStart
                }
            LBRACE -> depth += 1
            RBRACE -> depth -= 1
        }
        advance()
    }
    return null
}

private fun KotlinLexer.findBlockEnd(): Int? {
    var depth: Int = 0
    while (tokenType != null) {
        when (tokenType) {
            LBRACE -> depth += 1
            RBRACE -> {
                if (depth == 1) {
                    return tokenStart
                }
                depth -= 1
            }
        }
        advance()
    }
    return null
}
