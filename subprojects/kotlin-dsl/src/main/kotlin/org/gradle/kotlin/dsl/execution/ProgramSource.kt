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


data class ProgramSource internal constructor(val path: String, val contents: ProgramText) {

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

        internal
        fun from(string: String) =
            ProgramText(string.replace("\r\n", "\n"))
    }

    internal
    fun erase(ranges: List<IntRange>): ProgramText =
        if (ranges.isEmpty()) this
        else ProgramText(text.erase(ranges))

    fun preserve(vararg ranges: IntRange): ProgramText =
        erase(complementOf(ranges))

    internal
    fun preserve(ranges: List<IntRange>): ProgramText =
        preserve(*ranges.toTypedArray())

    fun subText(range: IntRange): ProgramText =
        ProgramText(text.substring(range))

    internal
    fun lineNumberOf(index: Int): Int =
        text.lineAndColumnFor(index).first

    private
    fun complementOf(ranges: Array<out IntRange>): ArrayList<IntRange> {
        require(ranges.isNotEmpty())

        val sortedRanges = ranges.sortedBy { it.first }
        val rangesToErase = ArrayList<IntRange>(ranges.size + 1)

        var last = 0
        for (range in sortedRanges) {
            rangesToErase.add(last until range.first)
            last = range.last + 1
        }

        val lastIndexToPreserve = sortedRanges.last().last
        if (lastIndexToPreserve < text.lastIndex) {
            rangesToErase.add(lastIndexToPreserve + 1..text.lastIndex)
        }
        return rangesToErase
    }
}


internal
fun text(string: String) = ProgramText.from(string)


internal
fun ProgramSource.fragment(identifier: IntRange, block: IntRange) =
    ProgramSourceFragment(this, ScriptSection(identifier, block))


internal
fun ProgramSource.fragment(section: ScriptSection) =
    ProgramSourceFragment(this, section)


data class ProgramSourceFragment internal constructor(
    val source: ProgramSource,
    internal val section: ScriptSection
) {
    internal
    val lineNumber: Int
        get() = source.contents.lineNumberOf(section.identifier.first)

    val range: IntRange
        get() = section.wholeRange

    override fun toString(): String =
        "ProgramSourceFragment(\"${source.text.subSequence(range)}\")"

    internal
    val blockString: String
        get() = source.text.substring(section.block)
}


internal
data class ScriptSection(
    val identifier: IntRange,
    val block: IntRange
) {

    val wholeRange
        get() = identifier.first..block.last
}


private
fun String.erase(ranges: List<IntRange>): String {

    val result = StringBuilder(length)

    for (range in ranges.sortedBy { it.first }) {

        result.append(this, result.length, range.first)

        for (ch in subSequence(range)) {
            result.append(
                if (Character.isWhitespace(ch)) ch
                else ' '
            )
        }
    }

    // TODO:partial-evaluator this doesn't make sense to be here
    val lastNonWhitespace = indexOfLast { !Character.isWhitespace(it) }
    if (lastNonWhitespace > result.length) {
        result.append(this, result.length, lastNonWhitespace + 1)
    }

    return result.toString()
}
