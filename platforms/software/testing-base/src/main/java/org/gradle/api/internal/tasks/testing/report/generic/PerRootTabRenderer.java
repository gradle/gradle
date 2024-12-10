/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.io.CharStreams;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.xml.SimpleMarkupWriter;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

public abstract class PerRootTabRenderer extends ReportRenderer<TestTreeModel, SimpleHtmlWriter> {
    protected final String rootName;
    private TestTreeModel currentModel;

    public PerRootTabRenderer(String rootName) {
        this.rootName = rootName;
    }

    @Override
    public void render(TestTreeModel model, SimpleHtmlWriter htmlWriter) throws IOException {
        this.currentModel = model;
        TestTreeModel.PerRootInfo info = model.getPerRootInfo().get(rootName);
        render(info, htmlWriter);
    }

    protected final TestTreeModel getCurrentModel() {
        if (currentModel == null) {
            throw new IllegalStateException("No model set");
        }
        return currentModel;
    }

    protected abstract void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException;

    public static final class ForSummary extends PerRootTabRenderer {
        public ForSummary(String rootName) {
            super(rootName);
        }

        @Override
        protected void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div");
            renderSummary(info, htmlWriter, info.getResult());
            renderChildren(info, htmlWriter);
            htmlWriter.endElement();
        }

        private static void renderSummary(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter, SerializableTestResult testResult) throws IOException {
            htmlWriter.startElement("div").attribute("class", "summary");
            htmlWriter.startElement("table");
            htmlWriter.startElement("tr");

            htmlWriter.startElement("td");

            renderSummaryGroup(info, htmlWriter, testResult);

            htmlWriter.endElement();

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox " + getStatusClass(testResult.getResultType()) + " successRate");
            htmlWriter.startElement("div").attribute("class", "percent").characters(getFormattedSuccessRate(info)).endElement();
            htmlWriter.startElement("p").characters("successful").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();
        }

        private static void renderSummaryGroup(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter, SerializableTestResult testResult) throws IOException {
            htmlWriter.startElement("div").attribute("class", "summaryGroup");
            htmlWriter.startElement("table");
            htmlWriter.startElement("tr");

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox");
            htmlWriter.startElement("div").attribute("class", "counter").characters(Integer.toString(info.getTotalLeafCount())).endElement();
            htmlWriter.startElement("p").characters("tests").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox");
            htmlWriter.startElement("div").attribute("class", "counter").characters(Integer.toString(info.getFailedLeafCount())).endElement();
            htmlWriter.startElement("p").characters("failures").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox");
            htmlWriter.startElement("div").attribute("class", "counter").characters(Integer.toString(info.getSkippedLeafCount())).endElement();
            htmlWriter.startElement("p").characters("skipped").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox duration");
            htmlWriter.startElement("div").attribute("class", "counter").characters(getFormattedDuration(testResult)).endElement();
            htmlWriter.startElement("p").characters("duration").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();
        }

        private static String getFormattedDuration(SerializableTestResult testResult) {
            return TimeFormatting.formatDurationVeryTerse(testResult.getDuration());
        }

        private void renderChildren(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            if (info.getChildren().isEmpty()) {
                return;
            }

            SimpleMarkupWriter writer = htmlWriter.startElement("table");
            boolean methodNameColumnExists = methodNameColumnExists();

            renderTableHead(writer, determineTableHeaders(methodNameColumnExists));

            for (TestTreeModel model : getCurrentModel().getChildrenOf(rootName)) {
                renderTableRow(writer, model, determineTableRow(model, methodNameColumnExists));
            }
            htmlWriter.endElement();
        }

        private List<String> determineTableRow(TestTreeModel test, boolean methodNameColumnExists) {
            SerializableTestResult result = test.getPerRootInfo().get(rootName).getResult();
            return methodNameColumnExists
                ? Arrays.asList(result.getDisplayName(), result.getName(), getFormattedDuration(result), getFormattedResultType(result.getResultType()))
                : Arrays.asList(result.getDisplayName(), getFormattedDuration(result), getFormattedResultType(result.getResultType()));
        }

        private static List<String> determineTableHeaders(boolean methodNameColumnExists) {
            return methodNameColumnExists ? Arrays.asList("Test", "Method name", "Duration", "Result") : Arrays.asList("Test", "Duration", "Result");
        }

        private static void renderTableHead(SimpleMarkupWriter writer, List<String> headers) throws IOException {
            writer.startElement("thead").startElement("tr");
            for (String header : headers) {
                writer.startElement("th").characters(header).endElement();
            }
            writer.endElement().endElement();
        }

        private void renderTableRow(SimpleMarkupWriter writer, TestTreeModel test, List<String> rowCells) throws IOException {
            SerializableTestResult result = test.getPerRootInfo().get(rootName).getResult();
            String statusClass = getStatusClass(result.getResultType());
            writer.startElement("tr");
            for (String cell : rowCells) {
                writer.startElement("td").attribute("class", statusClass).characters(cell).endElement();
            }
            writer.endElement();
        }

        private boolean methodNameColumnExists() {
            for (TestTreeModel child : getCurrentModel().getChildrenOf(rootName)) {
                SerializableTestResult result = child.getPerRootInfo().get(rootName).getResult();
                if (!result.getName().equals(result.getDisplayName())) {
                    return true;
                }
            }
            return false;
        }

        private static String getFormattedResultType(TestResult.ResultType resultType) {
            switch (resultType) {
                case SUCCESS:
                    return "passed";
                case FAILURE:
                    return "failed";
                case SKIPPED:
                    return "skipped";
                default:
                    throw new IllegalStateException();
            }
        }

        private static String getStatusClass(TestResult.ResultType resultType) {
            switch (resultType) {
                case SUCCESS:
                    return "success";
                case FAILURE:
                    return "failures";
                case SKIPPED:
                    return "skipped";
                default:
                    throw new IllegalStateException();
            }
        }

        private static String getFormattedSuccessRate(TestTreeModel.PerRootInfo info) {
            if (info.getTotalLeafCount() == 0) {
                return "-";
            }

            BigDecimal runTests = BigDecimal.valueOf(info.getTotalLeafCount());
            BigDecimal successful = BigDecimal.valueOf(info.getTotalLeafCount() - info.getFailedLeafCount());

            return successful.divide(runTests, 2, RoundingMode.DOWN).multiply(BigDecimal.valueOf(100)).intValue() + "%";
        }
    }

    public static final class ForOutput extends PerRootTabRenderer {
        private final SerializableTestResultStore.OutputReader outputReader;
        private final TestOutputEvent.Destination destination;

        public ForOutput(String rootName, SerializableTestResultStore.OutputReader outputReader, TestOutputEvent.Destination destination) {
            super(rootName);
            this.outputReader = outputReader;
            this.destination = destination;
        }

        @Override
        protected void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("span").attribute("class", "code")
                .startElement("pre")
                .characters("");
            try (Reader reader = outputReader.getOutput(info.getOutputId(), destination)) {
                CharStreams.copy(reader, htmlWriter);
            }
            htmlWriter.endElement()
                .endElement();
        }
    }

}
