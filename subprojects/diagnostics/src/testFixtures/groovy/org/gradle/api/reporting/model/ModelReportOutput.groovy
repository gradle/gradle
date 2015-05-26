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

import java.util.regex.Matcher

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ModelReportOutput {
    public static final String LINE_SEPARATOR = TextUtil.getPlatformLineSeparator()
    public static final int HEADING_LINE_NUMBER = 2
    public static final int FIRST_NODE_LINE_NUMBER = 6
    public static final String NODE_LEFT_PADDING = '    '
    public static final String NODE_SYMBOL = '+'
    List<String> normalizedLines
    List<String> nodeLines

    public ModelReportOutput(String text) {
        assert text
        normalizedLines = toPlatformLineSeparators(text.trim()).split(LINE_SEPARATOR)
        nodeLines = normalizedLines[FIRST_NODE_LINE_NUMBER..-1]
    }

    void hasTitle(String text) {
        lineAtIndexIs(HEADING_LINE_NUMBER, '------------------------------------------------------------')
        lineAtIndexIs(HEADING_LINE_NUMBER + 1, text)
        lineAtIndexIs(HEADING_LINE_NUMBER + 2, '------------------------------------------------------------')
    }

    void nodeContentEquals(String text) {
        assert text
        String[] subject = textToLines(text.trim())
        int successMarker = nodeLines.findIndexOf { line -> line == 'BUILD SUCCESSFUL' }
        def reportOnlyLines = nodeLines.subList(0, successMarker - 1)
        assert subject.length == reportOnlyLines.size()
        reportOnlyLines.eachWithIndex { String line, i ->
            assert line.startsWith(subject[i]): "\n\n Expected Line:${line} to start with:${subject[i]} line#($i)\n\n"
        }
    }

    void lineAtIndexIs(int num, String text) {
        assert normalizedLines[num] == text
    }

    /**
     * Useful for debugging line feed issues on different OS's
     */
    void debug() {
        println("Total report lines: ${normalizedLines.size()}")
        println("Numbered Lines: ")
        normalizedLines.eachWithIndex { l, i ->
            println "$i. $l"
        }
    }

    /**
     * Finds the first occurrence of the root node of the {@code closure} representing a {@link ModelReportBuilder} from within the entire report. i.e. a subtree
     * @see {@link ModelReportOutput#hasNodeStructure(groovy.lang.Closure)}
     */
    void hasNodeStructure(Closure closure) {
        hasNodeStructure(ModelReportBuilder.fromDsl(closure))
    }

    /**
     * Finds the first occurrence of the root node of {@code modelReportBuilder} from within the entire report. i.e. a subtree
     * Each node of the subtree is then verified against the expected structure.
     */
    void hasNodeStructure(ModelReportBuilder modelReportBuilder) {
        ReportNode expectedNode = modelReportBuilder.rootNode
        def parsedNodes = parseNodes()
        def actualNode = parsedNodes.findFirstByName(expectedNode.name)
        assert actualNode: "Could not find the first node to begin comparison"
        checkNodes(actualNode, expectedNode)
    }

    /**
     * A fuzzy assertion which recursively asserts that:
     * - {@code excepted} has, at a minimum, has the same number of and node names as {@actual}
     * - Where an {@code excepted} node has properties (value, types, etc.) the vales of those properties will be asserted with {@actual}
     *
     * @param actual Some or all of the model report used as the reference to verify {@code excepted}
     * @param expected A representation of some or all of the model report to be checked against {@code actual}
     */
    void checkNodes(ReportNode actual, ReportNode expected) {
        assert actual.nodes.size() == expected.nodes.size()
        assert actual.name == expected.name

        if (expected.value) {
            assert actual.value == expected.value
        }
        if (expected.type) {
            assert actual.type == expected.type
        }
        expected.nodes.eachWithIndex { ReportNode node, int index ->
            checkNodes(actual.nodes[index], node)
        }
    }

    private ReportNode parseNodes() {
        ReportNode prev = null
        ReportNode root = null
        for (int i = 0; i < nodeLines.size(); i++) {
            String line = nodeLines[i]
            if (prev == null) {
                assert line == '+ model'
                root = new ReportNode(name: getNodeName(line), depth: 0);
                prev = root
            } else if (lineIsANode(line)) {
                int depth = getNodeDepth(line)
                if (depth > prev.getDepth()) {
                    ReportNode node = new ReportNode(name: getNodeName(line), depth: depth, parent: prev);
                    prev.nodes << node
                    prev = node
                } else if (depth == prev.getDepth()) {
                    ReportNode node = new ReportNode(name: getNodeName(line), depth: depth, parent: prev.getParent());
                    prev.getParent().nodes.add(node)
                    prev = node
                } else {
                    while (depth < prev.getDepth()) {
                        prev = prev.getParent();
                    }
                    ReportNode node = new ReportNode(name: getNodeName(line), depth: depth, parent: prev.getParent());
                    prev.getParent().nodes << node
                    prev = node
                }
            } else {
                setNodeProperties(line, prev)
            }
        }
        return root
    }

    String[] textToLines(String text) {
        return (toPlatformLineSeparators(text)).split(LINE_SEPARATOR)
    }

    String getNodeName(String line) {
        def matcher = lineIsANode(line)
        return matcher[0][1]
    }

    int getNodeDepth(String line) {
        return (line =~ /$NODE_LEFT_PADDING/).getCount()
    }


    void setNodeProperties(String line, ReportNode reportNode) {
        ['Value': 'value', 'Type': 'type'].each { String pattern, String prop ->
            def matcher = (line =~ /\| ${pattern}: (.+)\|$/)
            if (matcher) {
                String val = matcher[0][1]
                if (val) {
                    reportNode."$prop" = val.trim()
                }
            }
        }
    }

    Matcher lineIsANode(String line) {
        return line =~ /\$NODE_SYMBOL (.+)$/
    }
}
