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

class ModelReportParser {
    public static final String LINE_SEPARATOR = TextUtil.getPlatformLineSeparator()
    public static final int HEADING_LINE_NUMBER = 3
    public static final int FIRST_NODE_LINE_NUMBER = 6
    public static final String NODE_LEFT_PADDING = '    '
    public static final String NODE_SYMBOL = '+'
    public static final String END_OF_REPORT_MARKER = 'BUILD SUCCESSFUL'
    public static final String ROOT_NODE_MARKER = '+ model'
    private final String text

    ModelReportParser(String text) {
        this.text = text
    }

    public ParsedModelReport toModelReport() {
        validate()
        List<String> reportLines = textToLines(text)
        List<String> nodeLines = reportLines[FIRST_NODE_LINE_NUMBER..-1]
        return new ParsedModelReport(
            title: getTitle(reportLines),
            reportNode: parseNodes(nodeLines),
            reportLines: reportLines,
            nodeOnlyLines: nodeOnlyLines(nodeLines))
    }

    private void validate() {
        assert text: 'Report text must not be blank'
        List<String> reportLines = textToLines(text)
        assert reportLines.size() > FIRST_NODE_LINE_NUMBER: "Should have at least ${FIRST_NODE_LINE_NUMBER + 1} lines"
        assert text.contains(END_OF_REPORT_MARKER): "Expected to find an end of report marker '${END_OF_REPORT_MARKER}'"
        assert text.contains(ROOT_NODE_MARKER): "Expected to find the root node '${ROOT_NODE_MARKER}'"
    }

    private String getTitle(List<String> reportLines) {
        return reportLines[HEADING_LINE_NUMBER]
    }

    private ReportNode parseNodes(List<String> nodeLines) {
        ReportNode prev = null
        ReportNode root = null
        for (int i = 0; i < nodeLines.size(); i++) {
            String line = nodeLines[i]
            if (prev == null) {
                assert line == ROOT_NODE_MARKER
                root = new ReportNode(getNodeName(line))
                root.setDepth(0)
                prev = root
            } else if (lineIsANode(line)) {
                int depth = getNodeDepth(line)
                if (depth > prev.getDepth()) {
                    ReportNode node = new ReportNode(prev, getNodeName(line))
                    node.setDepth(depth)
                    prev = node
                } else if (depth == prev.getDepth()) {
                    ReportNode node = new ReportNode(prev.parent(), getNodeName(line))
                    node.setDepth(depth)
                    prev = node
                } else {
                    while (depth < prev.getDepth()) {
                        prev = prev.parent();
                    }
                    ReportNode node = new ReportNode(prev.parent(), getNodeName(line));
                    node.setDepth(depth)
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
        ['Value': 'nodeValue', 'Type': 'type'].each { String pattern, String prop ->
            def matcher = (line =~ /\| ${pattern}: (.+)\|$/)
            if (matcher) {
                String val = matcher[0][1]
                if (val) {
                    reportNode.attributes()[prop] = val.trim()
                }
            }
        }
    }

    Matcher lineIsANode(String line) {
        return line =~ /\$NODE_SYMBOL (.+)$/
    }

    private List<String> nodeOnlyLines(List<String> nodeLines) {
        int successMarker = nodeLines.findIndexOf { line -> line == END_OF_REPORT_MARKER }
        nodeLines.subList(0, successMarker - 1)
    }
}
