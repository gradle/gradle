/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Specification

import static org.gradle.util.internal.ArgumentsSplitter.split

class ArgumentsSplitterTest extends Specification {

    def breaksUpEmptyCommandLineIntoEmptyList() {
        expect:
        split('') == []
    }

    def breaksUpWhitespaceOnlyCommandLineIntoEmptyList() {
        expect:
        split(' \t ') == []
    }

    def breaksUpCommandLineIntoSpaceSeparatedArgument() {
        expect:
        split('a') == ['a']
        split('a b\tc') == ['a', 'b', 'c']
    }

    def ignoresExtraWhiteSpaceBetweenArguments() {
        expect:
        split('  a \t') == ['a']
        split('a  \t\t b ') == ['a', 'b']
    }

    def breaksUpCommandLineIntoDoubleQuotedArguments() {
        expect:
        split('"a b c"') == ['a b c']
        split('a "b c d" e') == ['a', 'b c d', 'e']
        split('a "  b c d  "') == ['a', '  b c d  ']
    }

    def breaksUpCommandLineIntoSingleQuotedArguments() {
        expect:
        split("'a b c'") == ['a b c']
        split("a 'b c d' e") == ['a', 'b c d', 'e']
        split("a '  b c d  '") == ['a', '  b c d  ']
    }

    def canHaveEmptyQuotedArgument() {
        expect:
        split('""') == ['']
        split("''") == ['']
    }

    def canHaveQuoteInsideQuotedArgument() {
        expect:
        split('"\'quoted\'"') == ['\'quoted\'']
        split("'\"quoted\"'") == ['"quoted"']
    }

    def argumentCanHaveQuotedAndUnquotedParts() {
        expect:
        split('a"b "c') == ['ab c']
        split("a'b 'c") == ['ab c']
    }

    def canHaveMissingEndQuote() {
        expect:
        split('"a b c') == ['a b c']
    }
}
