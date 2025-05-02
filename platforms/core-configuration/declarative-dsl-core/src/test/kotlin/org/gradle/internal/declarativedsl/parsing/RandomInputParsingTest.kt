package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.stream.Collectors

@RunWith(Parameterized::class)
class RandomInputParsingTest(
    @Suppress("unused") private val id: String, // used in test case name
    private val file: File
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Any> {
            val parameters = mutableListOf<Any>()

            val inputDirectory = this::class.java.getResource("random")?.file?.let { File(it) } ?: error("missing input folder")
            inputDirectory.walk().forEach {
                if (it.isFile) {
                    parameters.add(arrayOf(it.relativeTo(inputDirectory).toString(), it))
                }
            }

            return parameters
        }
    }

    @Test
    fun test() {
        val input = file.readText(Charsets.UTF_8).unescapeUnicode()
        val parsingResult = ParseTestUtil.parse(input)
        assertTrue(
            "Failures while parsing file $id: ${format(parsingResult.allFailures)}",
            parsingResult.allFailures.isEmpty()
        )
    }

    private
    fun String.unescapeUnicode() = replace("\\\\u([0-9A-Fa-f]{4})".toRegex()) {
        String(Character.toChars(it.groupValues[1].toInt(radix = 16)))
    }

    private
    fun format(failures: List<SingleFailureResult>): List<String> =
        failures.stream()
            .map { "\n\t${formatFailure(it)}" }
            .collect(Collectors.toList())

    private
    fun formatFailure(failure: SingleFailureResult) =
        when (failure) {
            is UnsupportedConstruct -> "${location(failure.erroneousSource)}: unsupported language feature: ${failure.languageFeature}"
            is ParsingError -> "${location(failure.erroneousSource)}: parsing error: ${failure.message}"
        }

    private
    fun location(ast: SourceData): String =
        if (ast.lineRange.first != -1) "${ast.lineRange.first}:${ast.startColumn}" else ""

}
