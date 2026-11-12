/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.internal.hash.Hashing
import org.gradle.util.internal.TextUtil.convertLineSeparatorsToUnix
import java.io.File

object KotlinScriptHashing {

    fun hashOf(scriptText: CharSequence) =
        hashOfNormalisedString(convertLineSeparatorsToUnix(scriptText.toString()))

    fun hashOfNormalisedString(scriptText: CharSequence) =
        Hashing.hashString(scriptText).toString()

    /**
     * Stable key of a precompiled script, shared by Gradle, the Kotlin compiler and the IDE model builders.
     *
     * Derived from the declared package and the file name only, so it is independent of the script body,
     * of line separators, of a byte order mark and of absolute paths.
     * The package is found by [ScriptPackageScanner], which has no dependency on Kotlin compiler internals,
     * so the same code runs on all sides.
     */
    fun precompiledScriptKeyOfText(scriptText: CharSequence, scriptFileName: String): String =
        Hashing.hashString("${ScriptPackageScanner.packageOf(scriptText).orEmpty()}/$scriptFileName").toString()

    fun precompiledScriptKeyOfFile(scriptFile: File): String =
        precompiledScriptKeyOfText(scriptFile.readText(), scriptFile.name)
}


/**
 * Finds the package declared by a Kotlin script without the Kotlin lexer.
 *
 * Skips, in order: a byte order mark, a shebang line, then any whitespace (including `\r`),
 * line comments, nested block comments and `@file:` annotations, and reads `package a.b.c`.
 * Returns `null` when the first other token is not `package`.
 */
internal
object ScriptPackageScanner {

    fun packageOf(text: CharSequence): String? {
        var i = 0
        if (i < text.length && text[i] == '\uFEFF') i++
        if (text.startsWith("#!", i)) i = lineEndOf(text, i)
        while (true) {
            i = skipTrivia(text, i)
            if (!startsWithWord(text, i, "@file")) break
            i = skipFileAnnotation(text, i + "@file".length) ?: return null
        }
        if (!startsWithWord(text, i, "package")) return null
        i = skipTrivia(text, i + "package".length)
        val name = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1).takeIf { it > 0 } ?: return null
                    name.append(text, i, end + 1)
                    i = end + 1
                }
                c == '.' || Character.isJavaIdentifierPart(c) -> {
                    name.append(c)
                    i++
                }
                else -> break
            }
        }
        return name.toString().takeIf { it.isNotEmpty() }
    }

    private
    fun skipFileAnnotation(text: CharSequence, from: Int): Int? {
        var i = skipTrivia(text, from)
        if (i >= text.length || text[i] != ':') return null
        i = skipTrivia(text, i + 1)
        if (i < text.length && text[i] == '[') return skipBalanced(text, i, '[', ']')
        while (i < text.length && (text[i] == '.' || Character.isJavaIdentifierPart(text[i]))) i++
        if (i < text.length && text[i] == '<') i = skipBalanced(text, i, '<', '>') ?: return null
        if (i < text.length && text[i] == '(') i = skipBalanced(text, i, '(', ')') ?: return null
        return i
    }

    private
    fun skipBalanced(text: CharSequence, from: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = from
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("\"\"\"", i) -> i = text.indexOf("\"\"\"", i + 3).takeIf { it >= 0 }?.plus(3) ?: return null
                c == '"' || c == '\'' -> i = skipQuoted(text, i, c) ?: return null
                text.startsWith("//", i) || text.startsWith("/*", i) -> i = skipTrivia(text, i)
                else -> {
                    if (c == open) depth++
                    else if (c == close && --depth == 0) return i + 1
                    i++
                }
            }
        }
        return null
    }

    private
    fun skipQuoted(text: CharSequence, from: Int, quote: Char): Int? {
        var i = from + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return null
    }

    private
    fun skipTrivia(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length) {
            i = when {
                Character.isWhitespace(text[i]) -> i + 1
                text.startsWith("//", i) -> lineEndOf(text, i)
                text.startsWith("/*", i) -> blockCommentEndOf(text, i)
                else -> return i
            }
        }
        return i
    }

    private
    fun blockCommentEndOf(text: CharSequence, from: Int): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            when {
                text.startsWith("/*", i) -> { depth++; i += 2 }
                text.startsWith("*/", i) -> { i += 2; if (--depth == 0) return i }
                else -> i++
            }
        }
        return i
    }

    private
    fun lineEndOf(text: CharSequence, from: Int): Int =
        text.indexOf('\n', from).let { if (it < 0) text.length else it + 1 }

    private
    fun startsWithWord(text: CharSequence, at: Int, word: String): Boolean =
        text.startsWith(word, at) && (at + word.length >= text.length || !Character.isJavaIdentifierPart(text[at + word.length]))
}
