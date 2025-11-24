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
import com.google.common.net.MediaType;
import org.apache.commons.lang3.stream.Streams;
import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.TestOutputReader;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.Pair;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabsRenderer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.gradle.reporting.HtmlWriterTools.addClipboardCopyButton;

public abstract class PerRootTabRenderer extends ReportRenderer<TestTreeModel, SimpleHtmlWriter> {
    private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z").withZone(ZoneId.systemDefault());

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
        PerRootInfo info = model.getPerRootInfo().get(rootIndex).get(perRootInfoIndex);
        render(info, htmlWriter);
    }

    protected final TestTreeModel getCurrentModel() {
        if (currentModel == null) {
            throw new IllegalStateException("No model set");
        }
        return currentModel;
    }

    protected abstract void render(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException;

    private static String formatLogTime(Instant logTime) {
        return FORMATTER.format(logTime.atOffset(ZoneOffset.UTC));
    }

    public static final class ForSummary extends PerRootTabRenderer {
        public ForSummary(int rootIndex, int perRootInfoIndex) {
            super(rootIndex, perRootInfoIndex);
        }

        @Override
        protected void render(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div");
            renderSummary(info, htmlWriter);
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
            List<ChildEntry> children = Streams.of(getCurrentModel().getChildrenOf(rootIndex))
                .flatMap(t ->
                    t.getPerRootInfo().get(rootIndex).stream()
                        .map(p -> new ChildEntry(t, p))
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
            List<ChildEntry> children,
            ImmutableList.Builder<Pair<String, ChildTableRenderer>> childListRenderers
        ) {
            List<ChildEntry> matchedChildren = children.stream()
                .filter(e ->
                    e.perRootInfo.getResults().stream().anyMatch(
                        it -> it.getResultType() == resultType
                    )
                )
                .collect(Collectors.toList());
            if (!matchedChildren.isEmpty()) {
                childListRenderers.add(Pair.of(name, new ChildTableRenderer(matchedChildren)));
            }
        }

        private static void renderSummary(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div").attribute("class", "summary");
            htmlWriter.startElement("table");
            htmlWriter.startElement("tr");

            htmlWriter.startElement("td");

            renderSummaryGroup(info, htmlWriter);

            htmlWriter.endElement();

            htmlWriter.startElement("td");
            htmlWriter.startElement("div").attribute("class", "infoBox " + getStatusClass(getResultType(info)) + " successRate");
            htmlWriter.startElement("div").attribute("class", "percent").characters(getFormattedSuccessRate(info)).endElement();
            htmlWriter.startElement("p").characters("successful").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();
        }

        private static void renderSummaryGroup(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
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
            htmlWriter.startElement("div").attribute("class", "counter").characters(getFormattedDuration(info)).endElement();
            htmlWriter.startElement("p").characters("duration").endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();

            htmlWriter.endElement();
            htmlWriter.endElement();
            htmlWriter.endElement();
        }

        private static String getFormattedDuration(PerRootInfo info) {
            return info.getResults().stream()
                .map(r -> TimeFormatting.formatDurationVeryTerse(r.getDuration()))
                .collect(Collectors.joining(" / "));
        }

        private void renderLeafDetails(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            if (info.getResults().size() > 1) {
                throw new IllegalStateException("Leaf nodes should only have one result");
            }
            SerializableTestResult result = info.getResults().get(0);

            boolean isSuccess = result.getResultType() == TestResult.ResultType.SUCCESS;
            boolean hasFailures = !result.getFailures().isEmpty();
            boolean hasAssumptionFailure =  result.getAssumptionFailure() != null;

            if (!isSuccess && (hasFailures || hasAssumptionFailure)) {
                htmlWriter.startElement("div").attribute("class", "result-details");

                htmlWriter.startElement("h3").characters(
                    result.getResultType() == TestResult.ResultType.FAILURE ? "Failure details" : "Skip details"
                ).endElement();

                String failureOutputId = "root-" + rootIndex + "-test-failure-" + result.getName();
                htmlWriter.startElement("span").attribute("class", "code");

                htmlWriter.startElement("pre").attribute("id", failureOutputId);
                if (hasFailures) {
                    for (SerializableFailure failure : result.getFailures()) {
                        renderFailure(failure, htmlWriter);
                    }
                }
                if (hasAssumptionFailure) {
                    renderFailure(result.getAssumptionFailure(), htmlWriter);
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

        private static final class ChildEntry {
            private final TestTreeModel model;
            private final PerRootInfo perRootInfo;

            private ChildEntry(TestTreeModel model, PerRootInfo perRootInfo) {
                this.model = model;
                this.perRootInfo = perRootInfo;
            }
        }

        private static final class ChildTableRenderer extends ReportRenderer<TestTreeModel, SimpleHtmlWriter> {
            private static final Comparator<ChildEntry> CHILD_PATH_COMPARATOR = Comparator.comparing(e -> e.model.getPath());

            private final List<ChildEntry> children;

            public ChildTableRenderer(List<ChildEntry> children) {
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
                        List<SerializableTestResult> results = child.perRootInfo.getResults();
                        // If the name is present at the front, even if we have multiple display names we
                        // don't need the name column
                        return !results.get(0).getName().equals(results.get(0).getDisplayName());
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

                List<ChildEntry> sortedByName = new ArrayList<>(children);
                sortedByName.sort(CHILD_PATH_COMPARATOR);

                for (ChildEntry pair : sortedByName) {
                    PerRootInfo perRootInfo = pair.perRootInfo;
                    String statusClass = getStatusClass(getResultType(perRootInfo));
                    htmlWriter.startElement("tr");

                    htmlWriter.startElement("td").attribute("class", statusClass);

                    String displayName = SerializableTestResult.getCombinedDisplayName(perRootInfo.getResults());
                    htmlWriter.startElement("a")
                        .attribute("href", GenericPageRenderer.getUrlTo(
                            model.getPath(), false,
                            pair.model.getPath(), pair.model.getChildren().isEmpty()
                        ))
                        .characters(displayName).endElement();
                    htmlWriter.endElement();

                    if (anyNameAndDisplayNameDiffer) {
                        htmlWriter.startElement("td").characters(perRootInfo.getResults().get(0).getName()).endElement();
                    }

                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getTotalLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getFailedLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(Integer.toString(perRootInfo.getSkippedLeafCount())).endElement();
                    htmlWriter.startElement("td").characters(getFormattedDuration(perRootInfo)).endElement();
                    htmlWriter.startElement("td").attribute("class", statusClass).characters(getFormattedSuccessRate(perRootInfo)).endElement();

                    htmlWriter.endElement();
                }
                htmlWriter.endElement();
            }
        }

        private static TestResult.ResultType getResultType(PerRootInfo info) {
            if (info.getChildren().isEmpty()) {
                // There should only be one result for leaf nodes
                if (info.getResults().size() > 1) {
                    throw new IllegalStateException("Leaf nodes should only have one result");
                }
                return info.getResults().get(0).getResultType();
            }
            // For container nodes, merge result types, with FAILURE > SUCCESS > SKIPPED.
            // Skipped is less than success because if there is any non-skipped child, the container is not skipped.
            TestResult.ResultType bestType = TestResult.ResultType.SKIPPED;
            for (SerializableTestResult result : info.getResults()) {
                if (result.getResultType() == TestResult.ResultType.FAILURE) {
                    // Promote to failure and stop checking, as we can't change any further
                    bestType = TestResult.ResultType.FAILURE;
                    break;
                } else if (result.getResultType() == TestResult.ResultType.SUCCESS) {
                    // Promote to success, keep checking in case there is a failure
                    bestType = TestResult.ResultType.SUCCESS;
                } else if (result.getResultType() != TestResult.ResultType.SKIPPED) {
                    throw new IllegalStateException("Unknown result type: " + result.getResultType());
                }
                // If it's skipped, do nothing, as we either leave as skipped, or would not change from success to skipped
            }
            return bestType;
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

        private static String getFormattedSuccessRate(PerRootInfo info) {
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
        protected void render(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            String outputId = "root-" + rootIndex + "-test-" + destination.name().toLowerCase(Locale.ROOT) + "-" + info.getResults().get(0).getName();
            htmlWriter.startElement("span").attribute("class", "code")
                .startElement("pre")
                .attribute("id", outputId);
            outputReader.useTestOutputEvents(
                info.getOutputEntries(), destination,
                event -> htmlWriter.characters(event.getMessage())
            );
            htmlWriter.endElement();
            addClipboardCopyButton(htmlWriter, outputId);
            htmlWriter.endElement();
        }
    }

    public static final class ForKeyValues extends PerRootTabRenderer {
        public ForKeyValues(int rootIndex, int perRootInfoIndex) {
            super(rootIndex, perRootInfoIndex);
        }

        @Override
        protected void render(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div").attribute("class", "metadata");
            List<DefaultTestKeyValueDataEvent> keyValues = ImmutableList.copyOf(Iterables.filter(info.getMetadatas(), DefaultTestKeyValueDataEvent.class));
            renderKeyValueTable(keyValues, htmlWriter);
            htmlWriter.endElement();
        }

        private static void renderKeyValueTable(List<DefaultTestKeyValueDataEvent> metadatas, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("table");
                renderKeyValueHeader(htmlWriter);
                renderKeyValueValues(metadatas, htmlWriter);
            htmlWriter.endElement();
        }

        private static void renderKeyValueHeader(SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("thead")
                .startElement("tr")
                    .startElement("th")
                        .characters("Time")
                    .endElement()
                    .startElement("th")
                        .characters("Key")
                    .endElement()
                    .startElement("th")
                        .characters("Value")
                    .endElement()
                .endElement()
            .endElement();
        }

        private static void renderKeyValueValues(Iterable<DefaultTestKeyValueDataEvent> metadatas, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("tbody");
            Iterator<DefaultTestKeyValueDataEvent> metadataIterator = metadatas.iterator();
            for (int metadataIdx = 0; metadataIterator.hasNext(); metadataIdx++) {
                DefaultTestKeyValueDataEvent metadata = metadataIterator.next();
                Map<String, String> elements = metadata.getValues();

                htmlWriter.startElement("tr").attribute("class", metadataIdx % 2 == 0 ? "even" : "odd");
                htmlWriter.startElement("td").attribute("rowspan", Integer.toString(metadata.getValues().size() + 1))
                    .startElement("span").attribute("class", "time")
                        .characters(formatLogTime(metadata.getLogTime()))
                    .endElement()
                .endElement();
                htmlWriter.endElement();

                for (Map.Entry<String, String> element : elements.entrySet()) {
                    htmlWriter.startElement("tr").attribute("class", metadataIdx % 2 == 0 ? "even" : "odd");
                    htmlWriter
                        .startElement("td").attribute("class", "key")
                        .characters(element.getKey())
                        .endElement()
                        .startElement("td").attribute("class", "value")
                        .characters(element.getValue())
                        .endElement();
                    htmlWriter.endElement();
                }
            }
            htmlWriter.endElement();
        }
    }


    public static final class ForFileAttachments extends PerRootTabRenderer {
        public ForFileAttachments(int rootIndex, int perRootInfoIndex) {
            super(rootIndex, perRootInfoIndex);
        }

        @Override
        protected void render(PerRootInfo info, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("div").attribute("class", "attachments");
            List<DefaultTestFileAttachmentDataEvent> keyValues = ImmutableList.copyOf(Iterables.filter(info.getMetadatas(), DefaultTestFileAttachmentDataEvent.class));
            renderFileAttachments(keyValues, htmlWriter);
            htmlWriter.endElement();
        }

        private static void renderFileAttachments(List<DefaultTestFileAttachmentDataEvent> metadatas, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("table");
            renderFileAttachmentHeader(htmlWriter);
            renderFileAttachmentValues(metadatas, htmlWriter);
            htmlWriter.endElement();
        }

        private static void renderFileAttachmentHeader(SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("thead")
                .startElement("tr")
                    .startElement("th")
                        .characters("Name")
                    .endElement()
                    .startElement("th")
                        .characters("Content")
                    .endElement()
                .endElement()
            .endElement();
        }

        private static void renderFileAttachmentValues(List<DefaultTestFileAttachmentDataEvent> metadatas, SimpleHtmlWriter htmlWriter) throws IOException {
            htmlWriter.startElement("tbody");
            for (int metadataIdx = 0; metadataIdx < metadatas.size(); metadataIdx++) {
                DefaultTestFileAttachmentDataEvent metadata = metadatas.get(metadataIdx);
                htmlWriter.startElement("tr").attribute("class", metadataIdx % 2 == 0 ? "even" : "odd");
                htmlWriter.startElement("td").attribute("class", "key").characters(metadata.getPath().getFileName().toString()).endElement();

                htmlWriter.startElement("td").attribute("class", "value");
                String possibleMediaType = metadata.getMediaType();
                if (possibleMediaType == null) {
                    // Might be a directory, just render this as a link
                    renderLink(htmlWriter, metadata.getPath());
                } else {
                    MediaType mediaType = MediaType.parse(possibleMediaType);
                    if (mediaType.is(MediaType.ANY_IMAGE_TYPE)) {
                        // render as image
                        renderImage(htmlWriter, metadata.getPath());
                    } else if (mediaType.is(MediaType.ANY_VIDEO_TYPE)) {
                        // render as video
                        renderVideo(htmlWriter, metadata.getPath());
                    } else {
                        // render as a link
                        renderLink(htmlWriter, metadata.getPath(), mediaType);
                    }
                }
                htmlWriter.endElement();

                htmlWriter.endElement();
            }
            htmlWriter.endElement();
        }

        private static void renderLink(SimpleHtmlWriter htmlWriter, Path path, MediaType mediaType) throws IOException {
            htmlWriter.startElement("a").attribute("href", htmlWriter.relativeLink(path)).characters(path.getFileName().toString() + " (" + mediaType + ")").endElement();
        }

        private static void renderLink(SimpleHtmlWriter htmlWriter, Path path) throws IOException {
            htmlWriter.startElement("a").attribute("href", htmlWriter.relativeLink(path)).characters(path.getFileName().toString()).endElement();
        }

        private static void renderImage(SimpleHtmlWriter htmlWriter, Path path) throws IOException {
            htmlWriter.startElement("img").attribute("src", htmlWriter.relativeLink(path)).attribute("alt", path.getFileName().toString()).endElement();
        }
        private static void renderVideo(SimpleHtmlWriter htmlWriter, Path path) throws IOException {
            htmlWriter.startElement("video")
                .attribute("src", htmlWriter.relativeLink(path)).attribute("controls", "")
                // If the browser doesn't support this video format, fallback to a link
                .startElement("a").attribute("href", htmlWriter.relativeLink(path)).characters("Download video").endElement()
            .endElement();
        }
    }
}
