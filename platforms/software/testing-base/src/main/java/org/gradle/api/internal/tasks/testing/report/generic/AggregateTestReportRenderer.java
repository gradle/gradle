/*
 * Copyright 2026 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import com.google.common.net.UrlEscapers;
import org.gradle.api.internal.tasks.testing.GeneratedTestReport;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener.Execution;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;
import org.gradle.reporting.TabsRenderer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a single-page aggregate test report that summarizes the test executions of a build and links
 * to their reports.
 * <p>
 * The page looks like the index page of a generic HTML test report, using the same styles and scripts,
 * so its markup follows the patterns from those pages.
 *
 * @see TabbedPageRenderer
 */
public final class AggregateTestReportRenderer extends TabbedPageRenderer<List<Execution>> {

    private static final String TITLE = "Aggregate test results";
    private static final URL STYLE_URL = Resources.getResource(AggregateTestReportRenderer.class, "style.css");

    private final Path reportDirectory;

    /**
     * @param reportDirectory the directory the report is written to, which links to other reports are relative to
     */
    public AggregateTestReportRenderer(Path reportDirectory) {
        this.reportDirectory = reportDirectory.toAbsolutePath();
    }

    @Override
    protected URL getStyleUrl() {
        return STYLE_URL;
    }

    @Override
    protected String getTitle() {
        return TITLE;
    }

    @Override
    protected ReportRenderer<List<Execution>, SimpleHtmlWriter> getHeaderRenderer() {
        return new ReportRenderer<>() {
            @Override
            public void render(List<Execution> model, SimpleHtmlWriter htmlWriter) {
            }
        };
    }

    @Override
    protected ReportRenderer<List<Execution>, SimpleHtmlWriter> getContentRenderer() {
        return new ReportRenderer<>() {
            @Override
            public void render(List<Execution> executions, SimpleHtmlWriter htmlWriter) throws IOException {
                // Generate a consistent ordering
                List<Execution> sorted = executions.stream().sorted(
                    Comparator.comparing(Execution::displayName)
                        .thenComparing(execution -> String.valueOf(indexFile(execution)))
                ).toList();
                renderSummary(sorted, htmlWriter);
                renderExecutions(sorted, htmlWriter);
            }
        };
    }

    private static void renderSummary(List<Execution> executions, SimpleHtmlWriter htmlWriter) throws IOException {
        long startTime = Long.MAX_VALUE;
        long endTime = Long.MIN_VALUE;
        long tests = 0;
        long failures = 0;
        long skipped = 0;
        boolean failed = false;
        for (Execution execution : executions) {
            startTime = Math.min(startTime, execution.startTime());
            endTime = Math.max(endTime, execution.endTime());
            tests += execution.testCount();
            failures += execution.failedCount();
            skipped += execution.skippedCount();
            failed |= execution.failed();
        }

        String duration = TimeFormatting.formatDurationVeryTerse(endTime - startTime);

        htmlWriter.startElement("div").attribute("class", "summary");
        htmlWriter.startElement("table");
        htmlWriter.startElement("tr");

        htmlWriter.startElement("td");
        htmlWriter.startElement("div").attribute("class", "summaryGroup");
        htmlWriter.startElement("table");
        htmlWriter.startElement("tr");
        renderInfoBox("infoBox", Long.toString(tests), "tests", htmlWriter);
        renderInfoBox("infoBox", Long.toString(failures), "failures", htmlWriter);
        renderInfoBox("infoBox", Long.toString(skipped), "skipped", htmlWriter);
        renderInfoBox("infoBox duration", duration, "duration", htmlWriter);
        htmlWriter.endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();

        htmlWriter.startElement("td");
        htmlWriter.startElement("div").attribute("class", "infoBox " + statusClass(failed, failures, skipped) + " successRate");
        htmlWriter.startElement("div").attribute("class", "percent").characters(formatSuccessRate(successRatePercent(tests, failures))).endElement();
        htmlWriter.startElement("p").characters("successful").endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();

        htmlWriter.endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();
    }

    private static void renderInfoBox(String boxClass, String counter, String label, SimpleHtmlWriter htmlWriter) throws IOException {
        htmlWriter.startElement("td");
        htmlWriter.startElement("div").attribute("class", boxClass);
        htmlWriter.startElement("div").attribute("class", "counter").characters(counter).endElement();
        htmlWriter.startElement("p").characters(label).endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();
    }

    /**
     * Renames test executions with the same name, like {@link GenericHtmlTestReportGenerator}
     * does for roots.
     */
    private static Map<Execution, String> uniqueNames(List<Execution> executions) {
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Execution execution : executions) {
            nameCounts.merge(execution.displayName(), 1, Integer::sum);
        }
        Map<String, Integer> nameRepeats = new HashMap<>();
        Map<Execution, String> names = new HashMap<>();
        for (Execution execution : executions) {
            String name = execution.displayName();
            if (nameCounts.get(name) > 1) {
                name = name + " (" + nameRepeats.merge(name, 1, Integer::sum) + ")";
            }
            names.put(execution, name);
        }
        return names;
    }

    private void renderExecutions(List<Execution> executions, SimpleHtmlWriter htmlWriter) throws IOException {
        Map<Execution, String> names = uniqueNames(executions);
        List<Execution> failed = executions.stream().filter(Execution::failed).collect(Collectors.toList());
        if (failed.isEmpty()) {
            // No need to render tabs if there is only one tab
            renderTable(executions, names, htmlWriter);
            return;
        }
        TabsRenderer<List<Execution>> tabs = new TabsRenderer<>();
        tabs.add("Failed", new TableRenderer(failed, names));
        tabs.add("All", new TableRenderer(executions, names));
        tabs.render(executions, htmlWriter);
    }

    private final class TableRenderer extends ReportRenderer<List<Execution>, SimpleHtmlWriter> {

        private final List<Execution> executions;
        private final Map<Execution, String> names;

        private TableRenderer(List<Execution> executions, Map<Execution, String> names) {
            this.executions = executions;
            this.names = names;
        }

        @Override
        public void render(List<Execution> model, SimpleHtmlWriter htmlWriter) throws IOException {
            renderTable(executions, names, htmlWriter);
        }

    }

    private void renderTable(List<Execution> executions, Map<Execution, String> names, SimpleHtmlWriter htmlWriter) throws IOException {
        htmlWriter.startElement("table").attribute("class", "test-results sortable");
        htmlWriter.startElement("thead");
        htmlWriter.startElement("tr");
        htmlWriter.startElement("th").characters("Test execution").endElement();
        htmlWriter.startElement("th").attribute("data-sort-default", "desc").characters("Tests").endElement();
        htmlWriter.startElement("th").attribute("data-sort-default", "desc").characters("Failures").endElement();
        htmlWriter.startElement("th").attribute("data-sort-default", "desc").characters("Skipped").endElement();
        htmlWriter.startElement("th").attribute("data-sort-default", "desc").characters("Duration").endElement();
        htmlWriter.startElement("th").characters("Success rate").endElement();
        htmlWriter.endElement();
        htmlWriter.endElement();

        htmlWriter.startElement("tbody");
        for (Execution execution : executions) {
            String statusClass = statusClass(execution.failed(), execution.failedCount(), execution.skippedCount());
            int successRatePercent = successRatePercent(execution.testCount(), execution.failedCount());
            long duration = execution.endTime() - execution.startTime();
            htmlWriter.startElement("tr");

            htmlWriter.startElement("td").attribute("class", statusClass);
            String name = names.get(execution);
            Path indexFile = indexFile(execution);
            if (indexFile != null) {
                htmlWriter.startElement("a").attribute("href", link(reportDirectory, indexFile)).characters(name).endElement();
            } else {
                htmlWriter.characters(name);
            }
            htmlWriter.endElement();

            htmlWriter.startElement("td").characters(Long.toString(execution.testCount())).endElement();
            htmlWriter.startElement("td").characters(Long.toString(execution.failedCount())).endElement();
            htmlWriter.startElement("td").characters(Long.toString(execution.skippedCount())).endElement();
            htmlWriter.startElement("td")
                .attribute("data-sort-value", Long.toString(duration))
                .characters(TimeFormatting.formatDurationVeryTerse(duration)).endElement();
            htmlWriter.startElement("td")
                .attribute("class", statusClass)
                .attribute("data-sort-value", Integer.toString(successRatePercent))
                .characters(formatSuccessRate(successRatePercent)).endElement();

            htmlWriter.endElement();
        }
        htmlWriter.endElement(); // tbody
        htmlWriter.endElement(); // table
    }

    /**
     * The HTML report of the given execution, which is linked from the summary table.
     */
    private static @Nullable Path indexFile(Execution execution) {
        return execution.report() instanceof GeneratedTestReport.Html html ? html.indexFile() : null;
    }

    /**
     * The CSS class for the status of an execution or of the whole report.
     * Like in {@link PerRootTabRenderer}, failures win over skipped tests, which win over success.
     * Unlike there, an execution can fail without failed tests, so its own result counts too.
     */
    private static String statusClass(boolean failed, long failures, long skipped) {
        if (failed || failures > 0) {
            return "failures";
        }
        if (skipped > 0) {
            return "skipped";
        }
        return "success";
    }

    /**
     * Like in {@link PerRootTabRenderer}, skipped tests count as successful, and -1 means there are no tests.
     */
    private static int successRatePercent(long tests, long failures) {
        if (tests == 0) {
            return -1;
        }
        return (int) ((tests - failures) * 100 / tests);
    }

    /**
     * A link to the given report, relative to the report directory where possible, so the reports
     * can be moved together.
     */
    static String link(Path reportDirectory, Path report) {
        Path absoluteReport = report.toAbsolutePath();
        if (!absoluteReport.getRoot().equals(reportDirectory.getRoot())) {
            // e.g. a different drive on Windows
            return absoluteReport.toUri().toString();
        }
        Path relative = reportDirectory.relativize(absoluteReport);
        return Joiner.on('/').join(
            Streams.stream(relative).map(segment -> UrlEscapers.urlPathSegmentEscaper().escape(segment.toString())).iterator()
        );
    }

    private static String formatSuccessRate(int successRatePercent) {
        return successRatePercent < 0 ? "-" : successRatePercent + "%";
    }

}
