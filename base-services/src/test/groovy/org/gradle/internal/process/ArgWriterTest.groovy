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

package org.gradle.internal.process

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ArgWriterTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final StringWriter writer = new StringWriter()
    final PrintWriter printWriter = new PrintWriter(writer, true)
    final ArgWriter argWriter = ArgWriter.unixStyle(printWriter)

    def "writes single argument to line"() {
        when:
        argWriter.args("-nologo")

        then:
        writer.toString() == toPlatformLineSeparators("-nologo\n")
    }

    def "writes multiple arguments to line"() {
        when:
        argWriter.args("-I", "some/dir")

        then:
        writer.toString() == toPlatformLineSeparators("-I some/dir\n")
    }

    def "quotes argument with whitespace"() {
        when:
        argWriter.args("ab c", "d e f")

        then:
        writer.toString() == toPlatformLineSeparators('"ab c" "d e f"\n')
    }

    def "javaStyle quotes argument with hash"() {
        def argWriter = ArgWriter.javaStyle(printWriter)

        when:
        argWriter.args("ab#c", "d#e#f")

        then:
        writer.toString() == toPlatformLineSeparators('"ab#c" "d#e#f"\n')
    }

    def "quotes empty argument"() {
        when:
        argWriter.args("a", "", "", "b")

        then:
        writer.toString() == toPlatformLineSeparators('a "" "" b\n')
    }

    def "escapes double quotes in argument"() {
        when:
        argWriter.args('"abc"', 'a" bc')

        then:
        writer.toString() == toPlatformLineSeparators('\\"abc\\" "a\\" bc"\n')
    }

    def "escapes backslash in argument"() {
        when:
        argWriter.args('a\\b', 'a \\ bc')

        then:
        writer.toString() == toPlatformLineSeparators('a\\\\b "a \\\\ bc"\n')
    }

    def "does not escape characters in windows style"() {
        def argWriter = ArgWriter.windowsStyle(printWriter)

        when:
        argWriter.args('a\\b', 'a "\\" bc')

        then:
        writer.toString() == toPlatformLineSeparators('a\\b "a "\\" bc"\n')
    }

    def "generates args file using system encoding"() {
        def argsFile = tmpDir.file("options.txt")
        def generator = ArgWriter.argsFileGenerator(argsFile, ArgWriter.unixStyleFactory())

        expect:
        generator.transform(["a", "\u0302", "a b c"]) == ["@${argsFile.absolutePath}"]
        argsFile.text == toPlatformLineSeparators('a\n\u0302\n"a b c"\n')
    }

    def "does not generate args file for empty args"() {
        def argsFile = tmpDir.file("options.txt")
        def generator = ArgWriter.argsFileGenerator(argsFile, ArgWriter.unixStyleFactory())

        expect:
        generator.transform([]) == []
        !argsFile.file
    }
}
