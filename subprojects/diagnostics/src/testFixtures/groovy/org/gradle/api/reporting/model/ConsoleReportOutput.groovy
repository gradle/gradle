/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model

import org.gradle.util.TextUtil

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ConsoleReportOutput {
    public static final String LINE_SEPARATOR = TextUtil.getPlatformLineSeparator()
    public static final int HEADING_LINE_NUMBER = 2
    public static final int FIRST_NODE_LINE_NUMBER = 6
    public static final int PADDING_SIZE = 4
    private String consoleOutput
    def lines

    ConsoleReportOutput(String consoleOutput) {
        this.consoleOutput = toPlatformLineSeparators(consoleOutput)
        lines = consoleOutput?.split(LINE_SEPARATOR)
    }

    void hasTitle(String text) {
        lineIs(HEADING_LINE_NUMBER, '------------------------------------------------------------')
        lineIs(HEADING_LINE_NUMBER + 1, text)
        lineIs(HEADING_LINE_NUMBER + 2, '------------------------------------------------------------')
    }

    void hasNodeStructure(String text) {
        List<String> nodeLines = lines[FIRST_NODE_LINE_NUMBER..-1]
        def subject = toPlatformLineSeparators(text).split(LINE_SEPARATOR)
        String firstToken = subject[0]
        int startPosition = nodeLines.findIndexOf { name -> name == firstToken }
        assert startPosition >= 0: "Failed to find the first node:$firstToken"
        int endPosition = startPosition + (subject.size() - 1)
        nodeLines[startPosition..endPosition].eachWithIndex { String line, i ->
            assert line.startsWith(subject[i]): "\n\n Expected Line:|${line}| to start with:|${subject[i]}| \n\n"
        }
    }

    void hasRootNode(String text) {
        assert lines[FIRST_NODE_LINE_NUMBER] == text
    }

    void lineIs(int num, String text) {
        assert lines[num] == text
    }

    void hasNodeAtDepth(String node, int depth) {
        String paddedNode = ((" " * PADDING_SIZE) * (depth - 1)) + node
        assert lines.findAll { it.startsWith(paddedNode) }
    }

    void debug() {
        println("Total report lines: ${lines.size()}")
        println("Original output: ${consoleOutput}")
        println("Numbered Lines: ")
        lines.eachWithIndex { l, i ->
            println "$i. $l"
        }

    }
}
