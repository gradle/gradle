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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class ArgWriterTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    TestFile argsFile

    def setup() {
        argsFile = tmpDir.file("args/file")
    }

    def "writes single argument to line"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(["-nologo"], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators("-nologo\n")
    }

    def "writes multiple arguments to multiple lines"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(["-I", "some/dir"], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators("-I\nsome/dir\n")
    }

    def "quotes argument with whitespace"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(["ab c", "d e f"], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('"ab c"\n"d e f"\n')
    }

    def "javaStyle quotes argument with hash"() {
        when:
        ArgWriter.javaStyle().generateArgsFile(["ab#c", "d#e#f"], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('"ab#c"\n"d#e#f"\n')
    }

    def "quotes empty argument"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(["a", "", "", "b"], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('a\n""\n""\nb\n')
    }

    def "escapes double quotes in argument"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(['"abc"', 'a" bc'], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('\\"abc\\"\n"a\\" bc"\n')
    }

    def "escapes backslash in argument"() {
        when:
        ArgWriter.unixStyle().generateArgsFile(['a\\b', 'a \\ bc'], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('a\\\\b\n"a \\\\ bc"\n')
    }

    def "does not escape characters in windows style"() {
        def argWriter = ArgWriter.windowsStyle()

        when:
        argWriter.generateArgsFile(['a\\b', 'a "\\" bc'], argsFile)

        then:
        argsFile.text == toPlatformLineSeparators('a\\b\n"a "\\" bc"\n')
    }

    @Issue("https://github.com/gradle/gradle/issues/30304")
    def "generates args file using native encoding"() {
        // Include a path with é (U+00E9), which encodes to 0xE9 in Windows-1252 but 0xC3 0xA9 in UTF-8.
        // On Windows with JDK 18+, NATIVE_CHARSET is Windows-1252 while Charset.defaultCharset() is UTF-8,
        // so these byte sequences differ — catching the bug described in gradle/gradle#30304.
        // The backslash is escaped by unixStyle() and the space triggers quoting.
        ArgWriter.unixStyle().generateArgsFile(["a", "\u0302", "a b c", 'Lé Gradle'], argsFile)

        expect:
        argsFile.bytes == toPlatformLineSeparators('a\n\u0302\n"a b c"\n"Lé Gradle"\n').getBytes(ArgWriter.NATIVE_CHARSET)
    }

    def "does not generate args file for empty args"() {
        def shortened = ArgWriter.unixStyle().generateArgsFile([], argsFile)

        expect:
        shortened == []
        !argsFile.file
    }

}
