/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.gradle.internal.hash.Hashing


data class ProgramSource(val path: String, val contents: ProgramText) {

    constructor(path: String, contents: String) : this(path, text(contents))

    val text: String
        get() = contents.text

    fun map(transform: (ProgramText) -> ProgramText) =
        ProgramSource(path, transform(contents))
}


/**
 * Normalised program text guaranteed to use `\n` as the only line terminator character.
 */
data class ProgramText private constructor(val text: String) {

    companion object {

        fun from(string: String) =
            ProgramText(string.replace("\r\n", "\n"))
    }

    fun erase(ranges: List<IntRange>): ProgramText =
        ProgramText(text.erase(ranges))

    fun preserve(range: IntRange): ProgramText {
        val ranges = ArrayList<IntRange>(2)
        if (range.start > 0) {
            ranges.add(0..(range.start - 1))
        }
        if (range.endInclusive < text.lastIndex) {
            ranges.add((range.endInclusive + 1)..text.lastIndex)
        }
        return erase(ranges)
    }
}


fun text(string: String) = ProgramText.from(string)


fun ProgramSource.fragment(identifier: IntRange, block: IntRange) =
    ProgramSourceFragment(this, ScriptSection(identifier, block))


fun ProgramSource.fragment(section: ScriptSection) =
    ProgramSourceFragment(this, section)


data class ProgramSourceFragment(
    val source: ProgramSource,
    val section: ScriptSection
)


data class ScriptSection(
    val identifier: IntRange,
    val block: IntRange
) {

    val wholeRange
        get() = identifier.start..block.endInclusive
}


internal
fun String.erase(ranges: List<IntRange>): String {

    if (ranges.isEmpty()) {
        return this
    }

    val result = StringBuilder(length)

    for (range in ranges) {

        result.append(this, result.length, range.start)

        for (ch in subSequence(range)) {
            result.append(
                if (Character.isWhitespace(ch)) ch
                else ' '
            )
        }
    }

    // TODO: this doesn't make sense to be here
    val lastNonWhitespace = indexOfLast { !Character.isWhitespace(it) }
    if (lastNonWhitespace > result.length) {
        result.append(this, result.length, lastNonWhitespace + 1)
    }

    return result.toString()
}


internal
fun scriptSourceHash(source: String) =
    Hashing.md5().hashString(source)
