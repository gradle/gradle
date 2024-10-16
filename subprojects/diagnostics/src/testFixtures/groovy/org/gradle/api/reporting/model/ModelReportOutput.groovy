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

class ModelReportOutput {
    private final ParsedModelReport parsedModelReport

    static ModelReportOutput from(String text) {
        new ModelReportOutput(ModelReportParser.parse(text))
    }

    ModelReportOutput(ParsedModelReport parsedModelReport) {
        this.parsedModelReport = parsedModelReport
    }

    void hasTitle(String text) {
        assert parsedModelReport.title == text
    }

    void nodeContentEquals(String text) {
        assert text
        List<String> subject = text.trim().readLines()
        assert subject.size() == parsedModelReport.nodeOnlyLines.size()
        parsedModelReport.nodeOnlyLines.eachWithIndex { String line, i ->
            assert line == subject[i]: "\n\n Expected Line:${line} to start with:${subject[i]} line#($i)\n\n"
        }
    }

    /**
     * Finds the first occurrence of the root node of the {@code closure} representing a {@link ModelReportNodeBuilder} from within the entire report. i.e. a subtree
     * @see {@link ModelReportOutput#hasNodeStructure(groovy.lang.Closure)}
     */
    void hasNodeStructure(@DelegatesTo(ModelReportNodeBuilder) Closure<?> closure) {
        hasNodeStructure(ModelReportNodeBuilder.fromDsl(closure).get())
    }

    /**
     * Finds the first occurrence of the root node of {@code modelReportBuilder} from within the entire report. i.e. a subtree
     * Each node of the subtree is then verified against the expected structure.
     */
    void hasNodeStructure(ReportNode expectedNode) {
        def parsedNodes = parsedModelReport.reportNode
        def actualNode = parsedNodes.findFirstByName(expectedNode.name())
        assert actualNode: "Could not find the first node to begin comparison"
        checkNodes(actualNode, expectedNode)
    }

    /**
     * A fuzzy assertion which recursively asserts that:
     * - {@code excepted} has, at a minimum, has the same number of and node names as {@actual}
     * - Where an {@code excepted} node has properties (nodeValue, types, etc.) the vales of those properties will be asserted with {@actual}
     *
     * @param actual Some or all of the model report used as the reference to verify {@code excepted}
     * @param expected A representation of some or all of the model report to be checked against {@code actual}
     */
    void checkNodes(ReportNode actual, ReportNode expected) {
        assert actual.children().size() == expected.children().size()
        assert actual.name == expected.name
        ModelReportParser.NODE_ATTRIBUTES.each { String display, String property ->
            if (expected.attribute(property)) {
                assert actual.attribute(property) == expected.attribute(property)
            }
        }
        expected.children().eachWithIndex { ReportNode node, int index ->
            checkNodes(actual.children()[index], node)
        }
    }

    def getModelNode() {
        parsedModelReport.reportNode
    }
}
