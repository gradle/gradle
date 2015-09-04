/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain

import spock.lang.Specification

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ArgWriterTest extends Specification {
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

}
