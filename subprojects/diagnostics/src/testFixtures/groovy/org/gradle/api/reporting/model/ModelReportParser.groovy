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

import com.google.common.annotations.VisibleForTesting

import java.util.regex.Matcher

class ModelReportParser {

    public static final int HEADING_LINE_NUMBER = 3
    public static final int FIRST_NODE_LINE_NUMBER = 6
    public static final String NODE_LEFT_PADDING = '    '
    public static final String NODE_SYMBOL = '+'
    public static final String END_OF_REPORT_MARKER = 'BUILD SUCCESSFUL'
    public static final String ROOT_NODE_MARKER = '+ model'
    public static final LinkedHashMap<String, String> NODE_ATTRIBUTES = ['Value': 'nodeValue', 'Type': 'type', 'Creator': 'creator']

    static ParsedModelReport parse(String text) {
        validate(text)
        def reportLines = text.readLines()
        def nodeLines = reportLines[FIRST_NODE_LINE_NUMBER..-1]

        return new ParsedModelReport(
            getTitle(reportLines),
            reportLines,
            nodeOnlyLines(nodeLines),
            parseNodes(nodeLines)
        )
    }

    private static void validate(String text) {
        assert text: 'Report text must not be blank'
        def reportLines = text.readLines()
        assert reportLines.size() > FIRST_NODE_LINE_NUMBER: "Should have at least ${FIRST_NODE_LINE_NUMBER + 1} lines"
        assert text.contains(END_OF_REPORT_MARKER): "Expected to find an end of report marker '${END_OF_REPORT_MARKER}'"
        assert text.contains(ROOT_NODE_MARKER): "Expected to find the root node '${ROOT_NODE_MARKER}'"
    }

    private static String getTitle(List<String> reportLines) {
        return reportLines[HEADING_LINE_NUMBER]
    }

    private static ReportNode parseNodes(List<String> nodeLines) {
        ReportNode prev = null
        ReportNode root = null
        nodeLines.each { line ->
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
                        prev = prev.parent()
                    }
                    ReportNode node = new ReportNode(prev.parent(), getNodeName(line))
                    node.setDepth(depth)
                    prev = node
                }
            } else {
                if (isARuleLabel(line)) {
                    prev.attributes()['rules'] = []
                } else if (isARule(line)) {
                    prev.attributes()['rules'] << rule(line)
                } else {
                    setNodeProperties(line, prev)
                }
            }
        }
        return root
    }

    private static String rule(String line) {
        return line.replaceAll('⤷', '').trim()
    }

    private static boolean isARule(String line) {
        return line =~ /⤷ /
    }

    private static boolean isARuleLabel(String line) {
        return line =~ /$( +)| Rules:/
    }

    private static String getNodeName(String line) {
        def matcher = lineIsANode(line)
        return matcher[0][1]
    }

    private static int getNodeDepth(String line) {
        return (line =~ /$NODE_LEFT_PADDING/).getCount()
    }

    @VisibleForTesting
    static void setNodeProperties(String line, ReportNode reportNode) {
        NODE_ATTRIBUTES.each { String pattern, String prop ->
            def matcher = (line =~ /\| ${pattern}: (.+)$/)
            if (matcher) {
                String val = matcher[0][1]
                if (val) {
                    reportNode.attributes()[prop] = val.trim()
                }
            }
        }
    }

    private static Matcher lineIsANode(String line) {
        return line =~ /\$NODE_SYMBOL (.+)$/
    }

    private static List<String> nodeOnlyLines(List<String> nodeLines) {
        int successMarker = nodeLines.findIndexOf { line -> line == END_OF_REPORT_MARKER }
        nodeLines.subList(0, successMarker - 1)
    }
}
