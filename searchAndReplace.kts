import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

/*
 * Copyright 2022 the original author or authors.
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

val importLine = "import org.gradle.util.TestPrecondition"
val requiresRegex = Regex("(\\s*)@Requires\\(TestPrecondition\\.([^\\)]+)\\)(.*)")

val exceptions = mapOf(
    "NOT_EC2_AGENT" to "NotEC2Agent",
    "XCODE" to "HasXCode"
    "SMART" to "SmartTerminalAvailable"
)

fun camelizeFromShoutingSnakeCase(value: String): String {
    val components = value.split("_")
    return components.map {
        it.toLowerCase().replaceFirstChar {
            it.uppercaseChar()
        }
    }.joinToString("");
}

fun processMatch(line: String, matchResult: MatchResult): String {
    val spaces = matchResult.groups.get(1)!!.value;
    val matchedValue = matchResult.groups.get(2)!!.value;
    val mappedValue = exceptions.getOrDefault(
        matchedValue,
        camelizeFromShoutingSnakeCase(matchedValue)
    )
    val tail = matchResult.groups.get(3)?.value ?: ""

    println(line)
    println("  Spaces: ${spaces.length}")
    println("  Old value: ${matchedValue}")
    println("  New value: ${mappedValue}")

    return "${spaces}@Requires(UnitTestPreconditions.${mappedValue})${tail}"
}

fun processFile(filePath: Path) {
    val result = Files.lines(filePath, StandardCharsets.UTF_8).map {
        if (importLine == it) {
            "import org.gradle.util.UnitTestPreconditions"
        } else {
            val match = requiresRegex.matchEntire(it)
            if (match != null) processMatch(it, match!!) else it
        }
    }.collect(Collectors.joining(System.lineSeparator()))

    val file = filePath.toFile()
    file.writeText(result)
    file.appendText(System.lineSeparator())
}

class SourceVisitor : SimpleFileVisitor<Path>() {

    override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
        if (file!!.toString().endsWith(".java") || file!!.toString().endsWith(".groovy")) {
            processFile(file)
        }
        return super.visitFile(file, attrs)
    }
}

Files.walkFileTree(Path.of("subprojects/"), SourceVisitor())
