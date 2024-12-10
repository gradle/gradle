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
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
            if (info.getChildren().isEmpty()) {
                renderLeafDetails(info, htmlWriter);
            } else {
                renderChildren(htmlWriter);
            }
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

        private void renderLeafDetails(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            if (info.getResult().getResultType() != TestResult.ResultType.SUCCESS && !info.getResult().getFailures().isEmpty()) {
                htmlWriter.startElement("div").attribute("class", "result-details");

                htmlWriter.startElement("h3").characters(
                    info.getResult().getResultType() == TestResult.ResultType.FAILURE ? "Failure details" : "Skip details"
                ).endElement();

                htmlWriter.startElement("span").attribute("class", "code")
                    .startElement("pre")
                    .characters("");
                for (SerializableFailure failure : info.getResult().getFailures()) {
                    htmlWriter.characters(failure.getStackTrace() + "\n");
                }
                htmlWriter.endElement()
                    .endElement();

                htmlWriter.endElement();
            }
        }

        private void renderChildren(SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("table");
            htmlWriter.startElement("thead");
            htmlWriter.startElement("tr");

            htmlWriter.startElement("th").characters("Child").endElement();
            htmlWriter.startElement("th").characters("Tests").endElement();
            htmlWriter.startElement("th").characters("Failures").endElement();
            htmlWriter.startElement("th").characters("Skipped").endElement();
            htmlWriter.startElement("th").characters("Duration").endElement();
            htmlWriter.startElement("th").characters("Success rate").endElement();

            htmlWriter.endElement();
            htmlWriter.endElement();

            for (TestTreeModel child : getCurrentModel().getChildrenOf(rootName)) {
                TestTreeModel.PerRootInfo perRootInfo = child.getPerRootInfo().get(rootName);
                SerializableTestResult result = perRootInfo.getResult();
                String statusClass = getStatusClass(result.getResultType());
                htmlWriter.startElement("tr");
                htmlWriter.startElement("td").attribute("class", statusClass);
                htmlWriter.startElement("a")
                    .attribute("href", GenericPageRenderer.getUrlTo(getCurrentModel().getPath(), child.getPath()))
                    .characters(result.getDisplayName()).endElement();
                htmlWriter.endElement();
                htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getTotalLeafCount())).endElement();
                htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getFailedLeafCount())).endElement();
                htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getSkippedLeafCount())).endElement();
                htmlWriter.startElement("td").characters(getFormattedDuration(result)).endElement();
                htmlWriter.startElement("td").attribute("class", statusClass).characters(getFormattedSuccessRate(perRootInfo)).endElement();
                htmlWriter.endElement();
            }
            htmlWriter.endElement();
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
