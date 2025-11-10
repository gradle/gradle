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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.stream.Streams;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry.MetadataRenderer;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializedMetadata;
import org.gradle.api.internal.tasks.testing.results.serializable.TestOutputReader;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.Pair;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabsRenderer;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.gradle.reporting.HtmlWriterTools.addClipboardCopyButton;

public abstract class PerRootTabRenderer extends ReportRenderer<TestTreeModel, SimpleHtmlWriter> {
    protected final int rootIndex;
    // Should be private unlike rootIndex, as subclass access should use the passed-in `info` parameter
    private final int perRootInfoIndex;
    @Nullable
    private TestTreeModel currentModel;

    public PerRootTabRenderer(int rootIndex, int perRootInfoIndex) {
        this.rootIndex = rootIndex;
        this.perRootInfoIndex = perRootInfoIndex;
    }

    @Override
    public void render(TestTreeModel model, SimpleHtmlWriter htmlWriter) throws IOException {
        this.currentModel = model;
        TestTreeModel.PerRootInfo info = model.getPerRootInfo().get(rootIndex).get(perRootInfoIndex);
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
        public ForSummary(int rootIndex, int perRootInfoIndex) {
            super(rootIndex, perRootInfoIndex);
        }

        @Override
        protected void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div");
            renderSummary(info, htmlWriter, info.getResult());
            if (info.getChildren().isEmpty()) {
                renderLeafDetails(info, htmlWriter);
            } else {
                renderContainerDetails(htmlWriter);
            }
            htmlWriter.endElement();
        }

        private void renderContainerDetails(SimpleHtmlWriter htmlWriter) throws IOException {
            List<Pair<String, ChildTableRenderer>> childTableRenderers = getChildTableRenderers();

            ReportRenderer<TestTreeModel, SimpleHtmlWriter> renderer;
            if (childTableRenderers.size() == 1) {
                // No need to render tabs if there is only one tab
                renderer = Objects.requireNonNull(childTableRenderers.get(0).right);
            } else {
                TabsRenderer<TestTreeModel> tabsRenderer = new TabsRenderer<>();
                childTableRenderers.forEach(p -> tabsRenderer.add(p.left, p.right));
                renderer = tabsRenderer;
            }

            renderer.render(getCurrentModel(), htmlWriter);
        }

        private List<Pair<String, ChildTableRenderer>> getChildTableRenderers() {
            List<Pair<Path, TestTreeModel.PerRootInfo>> children = Streams.of(getCurrentModel().getChildrenOf(rootIndex))
                .flatMap(t ->
                    t.getPerRootInfo().get(rootIndex).stream()
                        .map(p -> Pair.of(t.getPath(), p))
                )
                .collect(Collectors.toList());
            ImmutableList.Builder<Pair<String, ChildTableRenderer>> childTableRenderers = ImmutableList.builder();
            addResultTabIfNeeded("Failed", TestResult.ResultType.FAILURE, children, childTableRenderers);
            addResultTabIfNeeded("Skipped", TestResult.ResultType.SKIPPED, children, childTableRenderers);
            childTableRenderers.add(Pair.of("All", new ChildTableRenderer(children)));
            return childTableRenderers.build();
        }

        private static void addResultTabIfNeeded(
            String name,
            TestResult.ResultType resultType,
            List<Pair<Path, TestTreeModel.PerRootInfo>> children,
            ImmutableList.Builder<Pair<String, ChildTableRenderer>> childListRenderers
        ) {
            List<Pair<Path, TestTreeModel.PerRootInfo>> matchedChildren = children.stream()
                .filter(p -> p.right.getResult().getResultType() == resultType)
                .collect(Collectors.toList());
            if (!matchedChildren.isEmpty()) {
                childListRenderers.add(Pair.of(name, new ChildTableRenderer(matchedChildren)));
            }
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
            boolean isSuccess = info.getResult().getResultType() == TestResult.ResultType.SUCCESS;
            boolean hasFailures = !info.getResult().getFailures().isEmpty();
            boolean hasAssumptionFailure =  info.getResult().getAssumptionFailure() != null;

            if (!isSuccess && (hasFailures || hasAssumptionFailure)) {
                htmlWriter.startElement("div").attribute("class", "result-details");

                htmlWriter.startElement("h3").characters(
                    info.getResult().getResultType() == TestResult.ResultType.FAILURE ? "Failure details" : "Skip details"
                ).endElement();

                String failureOutputId = "root-" + rootIndex + "-test-failure-" + info.getResult().getName();
                htmlWriter.startElement("span").attribute("class", "code");

                htmlWriter.startElement("pre").attribute("id", failureOutputId);
                if (hasFailures) {
                    for (SerializableFailure failure : info.getResult().getFailures()) {
                        renderFailure(failure, htmlWriter);
                    }
                }
                if (hasAssumptionFailure) {
                    renderFailure(info.getResult().getAssumptionFailure(), htmlWriter);
                }
                htmlWriter.endElement(); // pre

                addClipboardCopyButton(htmlWriter, failureOutputId);
                htmlWriter.endElement(); // span

                htmlWriter.endElement(); // div
            }
        }

        private void renderFailure(SerializableFailure failure, SimpleHtmlWriter htmlWriter) throws IOException {
            // There is confusion here over if we should show the message if there is a stack trace.
            // See https://github.com/gradle/gradle/issues/35176
            if (failure.getStackTrace().isEmpty()) {
                // We need to show the message
                htmlWriter.characters(failure.getMessage() + "\n");
            } else {
                htmlWriter.characters(failure.getStackTrace());
            }
            for (int i = 0; i < failure.getCauses().size(); i++) {
                htmlWriter.characters("Cause " + (i+1) + ": " + failure.getCauses().get(i) + "\n");
            }
        }

        private static final class ChildTableRenderer extends ReportRenderer<TestTreeModel, SimpleHtmlWriter> {
            private final List<Pair<Path, TestTreeModel.PerRootInfo>> children;

            public ChildTableRenderer(List<Pair<Path, TestTreeModel.PerRootInfo>> children) {
                this.children = children;
            }

            @Override
            public void render(TestTreeModel model, SimpleHtmlWriter htmlWriter) throws IOException {
                htmlWriter.startElement("table").attribute("class", "test-results");
                htmlWriter.startElement("thead");
                htmlWriter.startElement("tr");

                boolean anyNameAndDisplayNameDiffer = Iterables.any(
                    children,
                    child -> {
                        SerializableTestResult childResult = child.right.getResult();
                        return !childResult.getName().equals(childResult.getDisplayName());
                    }
                );

                htmlWriter.startElement("th").characters("Child").endElement();
                if (anyNameAndDisplayNameDiffer) {
                    htmlWriter.startElement("th").characters("Name").endElement();
                }
                htmlWriter.startElement("th").characters("Tests").endElement();
                htmlWriter.startElement("th").characters("Failures").endElement();
                htmlWriter.startElement("th").characters("Skipped").endElement();
                htmlWriter.startElement("th").characters("Duration").endElement();
                htmlWriter.startElement("th").characters("Success rate").endElement();

                htmlWriter.endElement();
                htmlWriter.endElement();

                List<Pair<Path, TestTreeModel.PerRootInfo>> sortedByName = new ArrayList<>(children);
                sortedByName.sort(Comparator.comparing(Pair::left));

                for (Pair<Path, TestTreeModel.PerRootInfo> pair : sortedByName) {
                    TestTreeModel.PerRootInfo perRootInfo = pair.right;
                    SerializableTestResult result = perRootInfo.getResult();
                    String statusClass = getStatusClass(result.getResultType());
                    htmlWriter.startElement("tr");

                    htmlWriter.startElement("td").attribute("class", statusClass);
                    htmlWriter.startElement("a")
                        .attribute("href", GenericPageRenderer.getUrlTo(model.getPath(), pair.left))
                        .characters(result.getDisplayName()).endElement();
                    htmlWriter.endElement();

                    if (anyNameAndDisplayNameDiffer) {
                        htmlWriter.startElement("td").characters(result.getName()).endElement();
                    }

                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getTotalLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getFailedLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getSkippedLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(getFormattedDuration(result)).endElement();
                    htmlWriter.startElement("td").attribute("class", statusClass).characters(getFormattedSuccessRate(perRootInfo)).endElement();

                    htmlWriter.endElement();
                }
                htmlWriter.endElement();
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
        private final TestOutputReader outputReader;
        private final TestOutputEvent.Destination destination;

        public ForOutput(int rootIndex, int perRootInfoIndex, TestOutputReader outputReader, TestOutputEvent.Destination destination) {
            super(rootIndex, perRootInfoIndex);
            this.outputReader = outputReader;
            this.destination = destination;
        }

        @Override
        protected void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            String outputId = "root-" + rootIndex + "-test-" + destination.name().toLowerCase(Locale.ROOT) + "-" + info.getResult().getName();
            htmlWriter.startElement("span").attribute("class", "code")
                .startElement("pre")
                .attribute("id", outputId);
            outputReader.useTestOutputEvents(
                info.getOutputEntry(), destination,
                event -> htmlWriter.characters(event.getMessage())
            );
            htmlWriter.endElement();
            addClipboardCopyButton(htmlWriter, outputId);
            htmlWriter.endElement();
        }
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedReturnValue"})
    public static final class ForMetadata extends PerRootTabRenderer {
        private final MetadataRendererRegistry metadataRendererRegistry;

        public ForMetadata(int rootIndex, int perRootInfoIndex, MetadataRendererRegistry metadataRendererRegistry) {
            super(rootIndex, perRootInfoIndex);
            this.metadataRendererRegistry = metadataRendererRegistry;
        }

        @Override
        protected void render(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div").attribute("class", "metadata");
                renderMetadataTable(info, htmlWriter);
            htmlWriter.endElement();
        }

        private void renderMetadataTable(TestTreeModel.PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("table");
                renderMetadataTableHeader(htmlWriter);
                renderMetadataTableBody(info.getMetadatas(), htmlWriter);
            htmlWriter.endElement();
        }

        private SimpleHtmlWriter renderMetadataTableHeader(SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("thead")
                .startElement("tr")
                    .startElement("th")
                        .characters("Time")
                    .endElement()
                    .startElement("th")
                        .characters("Key(s)")
                    .endElement()
                    .startElement("th")
                        .characters("Value(s)")
                    .endElement()
                .endElement()
            .endElement();

            return htmlWriter;
        }

        private SimpleHtmlWriter renderMetadataTableBody(List<SerializedMetadata> metadatas, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("tbody");
            for (int metadataIdx = 0; metadataIdx < metadatas.size(); metadataIdx++) {
                SerializedMetadata metadata = metadatas.get(metadataIdx);
                renderFirstMetadataElement(metadata, metadataIdx, htmlWriter);
                if (metadata.getEntries().size() > 1) {
                    List<SerializedMetadata.SerializedMetadataElement> additionalEntries = metadata.getEntries().subList(1, metadata.getEntries().size());
                    renderAdditionalMetadataElements(additionalEntries, metadataIdx, htmlWriter);
                }
            }
            htmlWriter.endElement();

            return htmlWriter;
        }

        private SimpleHtmlWriter renderFirstMetadataElement(SerializedMetadata metadata, int metadataIdx, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("tr").attribute("class", metadataIdx % 2 == 0 ? "even" : "odd");
                renderMetadataTimeCell(metadata, htmlWriter);
                renderMetadataKeyValueCells(metadata.getEntries().get(0), htmlWriter)
            .endElement();

            return htmlWriter;
        }

        private SimpleHtmlWriter renderAdditionalMetadataElements(List<SerializedMetadata.SerializedMetadataElement> elements, int metadataIdx, SimpleHtmlWriter htmlWriter) throws IOException {
            for (SerializedMetadata.SerializedMetadataElement element : elements) {
                htmlWriter.startElement("tr").attribute("class", metadataIdx % 2 == 1 ? "even" : "odd");
                    renderMetadataKeyValueCells(element, htmlWriter);
                htmlWriter.endElement();
            }

            return htmlWriter;
        }

        private SimpleHtmlWriter renderMetadataTimeCell(SerializedMetadata metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("td").attribute("rowspan", Integer.toString(metadata.getEntries().size()))
                .startElement("span").attribute("class", "time")
                    .characters(formatLogTime(metadata.getLogTime()))
                .endElement()
            .endElement();

            return htmlWriter;
        }

        private SimpleHtmlWriter renderMetadataKeyValueCells(SerializedMetadata.SerializedMetadataElement element, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter
                .startElement("td").attribute("class", "key")
                    .characters(element.getKey())
                .endElement()
                .startElement("td").attribute("class", "value");
                    renderMetadataValue(element, htmlWriter)
                .endElement();

            return htmlWriter;
        }

        private SimpleHtmlWriter renderMetadataValue(SerializedMetadata.SerializedMetadataElement element, SimpleHtmlWriter htmlWriter) throws IOException {
            MetadataRenderer renderer = metadataRendererRegistry.getRenderer(element.getValueType());

            try {
                return renderer.render(element.getValue(), htmlWriter);
            } catch (Exception e) {
                return (SimpleHtmlWriter) htmlWriter
                    .startElement("span").attribute("class", "unrenderable")
                        .characters("[error rendering value]")
                    .endElement();
            }
        }

        private String formatLogTime(long logTime) {
            Instant instant = Instant.ofEpochMilli(logTime);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
                .withZone(ZoneId.systemDefault());

            return formatter.format(instant);
        }
    }
}
