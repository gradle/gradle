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

package org.gradle.integtests.fixtures.problems

import groovy.json.JsonSlurper
import org.gradle.problems.internal.report.fixtures.HtmlReportDataReader

import static org.junit.Assert.assertTrue

/**
 * A fixture to perform assertions on the contents of the Problems Report.
 *
 * The counterpart of {@code ConfigurationCacheReportFixture} for the other kind of report the
 * configuration-cache-report library renders. Both reports carry their data the same way, so this
 * reads it with the same {@link HtmlReportDataReader}, and parses it into plain maps.
 */
class ProblemsReportFixture {

    private final File reportFile
    private final Map<String, Object> summary
    private final List<Map<String, Object>> problems

    ProblemsReportFixture(File reportFile) {
        this.reportFile = reportFile
        assertTrue("Problems report file '$reportFile' not found", reportFile.isFile())

        def reader = new HtmlReportDataReader(reportFile)
        def slurper = new JsonSlurper()
        this.summary = slurper.parseText(reader.readSummaryJson()) as Map<String, Object>
        this.problems = slurper.parseText(reader.readDiagnosticsJson()) as List<Map<String, Object>>
    }

    @Override
    String toString() {
        return "Problems report with ${problems.size()} problem(s) at $reportFile"
    }

    /**
     * The report summary: the context of the build and the counts of problems that were aggregated
     * instead of being reported individually.
     */
    Map<String, Object> getSummary() {
        return summary
    }

    /**
     * The individually reported problems, in the order the build reported them.
     */
    List<Map<String, Object>> getProblems() {
        return problems
    }

    /**
     * The id of each reported problem, as the dot-separated path of its group and id names,
     * e.g. {@code "generic.type"}.
     */
    List<String> getProblemIds() {
        return problems.collect { problem ->
            (problem['problemId'] as List<Map<String, Object>>)*.get('name').join('.')
        }
    }
}
