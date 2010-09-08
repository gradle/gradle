/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.foundation

import spock.lang.Specification

class CommandLineAssistantTest extends Specification {
    def breaksUpEmptyCommandLineIntoEmptyList() {
        expect:
        CommandLineAssistant.breakUpCommandLine('') == []
    }

    def breaksUpWhitespaceOnlyCommandLineIntoEmptyList() {
        expect:
        CommandLineAssistant.breakUpCommandLine(' \t ') == []
    }

    def breaksUpCommandLineIntoSpaceSeparatedArgument() {
        expect:
        CommandLineAssistant.breakUpCommandLine('a') == ['a']
        CommandLineAssistant.breakUpCommandLine('a b\tc') == ['a', 'b', 'c']
    }

    def ignoresExtraWhiteSpaceBetweenArguments() {
        expect:
        CommandLineAssistant.breakUpCommandLine('  a \t') == ['a']
        CommandLineAssistant.breakUpCommandLine('a  \t\t b ') == ['a', 'b']
    }

    def breaksUpCommandLineIntoDoubleQuotedArguments() {
        expect:
        CommandLineAssistant.breakUpCommandLine('"a b c"') == ['a b c']
        CommandLineAssistant.breakUpCommandLine('a "b c d" e') == ['a', 'b c d', 'e']
        CommandLineAssistant.breakUpCommandLine('a "  b c d  "') == ['a', '  b c d  ']
    }

    def breaksUpCommandLineIntoSingleQuotedArguments() {
        expect:
        CommandLineAssistant.breakUpCommandLine("'a b c'") == ['a b c']
        CommandLineAssistant.breakUpCommandLine("a 'b c d' e") == ['a', 'b c d', 'e']
        CommandLineAssistant.breakUpCommandLine("a '  b c d  '") == ['a', '  b c d  ']
    }

    def canHaveEmptyQuotedArgument() {
        expect:
        CommandLineAssistant.breakUpCommandLine('""') == ['']
        CommandLineAssistant.breakUpCommandLine("''") == ['']
    }
    
    def canHaveQuoteInsideQuotedArgument() {
        expect:
        CommandLineAssistant.breakUpCommandLine('"\'quoted\'"') == ['\'quoted\'']
        CommandLineAssistant.breakUpCommandLine("'\"quoted\"'") == ['"quoted"']
    }

    def argumentCanHaveQuotedAndUnquotedParts() {
        expect:
        CommandLineAssistant.breakUpCommandLine('a"b "c') == ['ab c']
        CommandLineAssistant.breakUpCommandLine("a'b 'c") == ['ab c']
    }

    def canHaveMissingEndQuote() {
        expect:
        CommandLineAssistant.breakUpCommandLine('"a b c') == ['a b c']
    }
}
