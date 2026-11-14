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

package org.gradle.api.internal.tasks.testing.report


import org.gradle.api.internal.tasks.testing.results.AggregateTestEventReporter
import org.gradle.test.fixtures.file.TestFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jspecify.annotations.Nullable

/**
 * Reads the aggregate test report, which is generated at the end of the build
 * by {@link AggregateTestEventReporter}.
 */
class AggregateTestReportFixture {

    final TestFile indexFile

    AggregateTestReportFixture(TestFile indexFile) {
        this.indexFile = indexFile
    }

    /**
     * Create a new fixture to verify the test report of the build in the given root build directory.
     */
    static AggregateTestReportFixture of(TestFile rootBuildDir) {
        return new AggregateTestReportFixture(rootBuildDir.file("build/reports/aggregate-test-results/index.html"))
    }

    record Row(
        String name,
        /** The link to the execution's report, as written in the page, or null if there is none. */
        @Nullable String href,
        /** The result of the execution, one of "passed", "failed" or "skipped". */
        String result,
        long tests,
        long passed,
        long failed,
        long skipped
    ) {}

    /**
     * The counters of the summary at the top of the report, by label, plus the success rate. The duration is left out, as it varies.
     */
    Map<String, String> getTotals() {
        Map<String, String> totals = [:]
        for (Element box : document.select("div.summaryGroup div.infoBox:not(.duration)")) {
            totals[box.selectFirst("p").text()] = box.selectFirst("div.counter").text()
        }
        totals["successful"] = document.selectFirst("div.successRate div.percent").text()
        return totals
    }

    /**
     * The rows of the table of all test executions, in the order they are displayed.
     */
    List<Row> getRows() {
        return rowsOf(table("All"))
    }

    /**
     * The rows of the table of failed test executions, in the order they are displayed.
     */
    List<Row> getFailedRows() {
        def failedTab = tab("Failed")
        return failedTab == null ? [] : rowsOf(failedTab.selectFirst("table.test-results"))
    }

    Row row(String name) {
        def matching = rows.findAll { it.name() == name }
        assert matching.size() == 1: "Expected exactly one row named '$name' in ${rows*.name()}"
        return matching[0]
    }

    /**
     * Resolves the link of the given row to the file it points to.
     */
    TestFile linkedReport(String name) {
        def href = row(name).href()
        assert href != null: "Row '$name' has no link"
        return new TestFile(indexFile.parentFile.toURI().resolve(href))
    }

    private Document getDocument() {
        indexFile.assertIsFile()
        return Jsoup.parse(indexFile, "UTF-8")
    }

    private Element tab(String title) {
        return document.select("div.tab").find { it.selectFirst("> h2")?.text() == title }
    }

    private Element table(String tabTitle) {
        def tab = tab(tabTitle)
        // Without failed or skipped executions, there are no tabs above the table
        return tab == null ? document.selectFirst("table.test-results") : tab.selectFirst("table.test-results")
    }

    private static final Map<String, String> RESULTS = [success: "passed", failures: "failed", skipped: "skipped"]

    private static List<Row> rowsOf(Element table) {
        List<Row> rows = []
        for (Element tr : table.select("> tbody > tr")) {
            def cells = tr.select("> td")
            def name = cells[0].text()
            def link = cells[0].selectFirst("a")
            def resultClass = cells[0].className()
            long tests = cells[1].text() as long
            long failed = cells[2].text() as long
            long skipped = cells[3].text() as long
            rows << new Row(
                name,
                link?.attr("href"),
                RESULTS[resultClass],
                tests,
                tests - failed - skipped,
                failed,
                skipped
            )
        }
        return rows
    }

}
