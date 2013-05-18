/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.results;

import com.googlecode.jatl.Html;
import org.gradle.performance.fixture.BaselineVersion;
import org.gradle.performance.fixture.PerformanceResults;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ReportGenerator {
    private final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ReportGenerator() {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    void generate(final ResultsStore store, File outputDirectory) {
        try {
            File outputFile = new File(outputDirectory, "index.html");
            GFileUtils.parentMkdirs(outputFile);
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
            try {
                new Html(writer) {{
                    html();
                        head();
                            meta().httpEquiv("Content-Type").content("text/html; charset=utf-8");
                            style().text("body { font-family: sans-serif; margin: 3em; } h2 { font-size: 14pt; margin-top: 2em; } table { width: 100%; } th { text-align: left; } #footer { margin-top: 4em; font-size: 8pt; }").end();
                            title().text("Profile report").end();
                        end();
                        body();
                        div().id("content");
                            h2().text("All tests").end();
                            List<String> testNames = store.getTestNames();
                            ul();
                                for (String testName : testNames) {
                                    li();
                                        a().href(String.format("#%s", testName)).text(testName).end();
                                    end();
                                }
                            end();
                            for (String testName : testNames) {
                                TestExecutionHistory testHistory = store.getTestResults(testName);
                                h2();
                                    a().name(testName).end();
                                    text(testName);
                                end();
                                table();
                                    tr();
                                        th().end();
                                        th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average execution time").end();
                                        th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average heap usage").end();
                                    end();
                                    tr();
                                        th().text("Date").end();
                                        for (String version : testHistory.getBaselineVersions()) {
                                            th().text(version).end();
                                        }
                                        th().text("Current").end();
                                        for (String version : testHistory.getBaselineVersions()) {
                                            th().text(version).end();
                                        }
                                        th().text("Current").end();
                                    end();
                                    for (PerformanceResults performanceResults : testHistory.getResults()) {
                                        tr();
                                            td().text(format.format(new Date(performanceResults.getTestTime()))).end();
                                            for (String version : testHistory.getBaselineVersions()) {
                                                BaselineVersion baselineVersion = performanceResults.baseline(version);
                                                if (baselineVersion.getResults().isEmpty()) {
                                                    td().text("").end();
                                                } else {
                                                    td().text(baselineVersion.getResults().avgTime().format()).end();
                                                }
                                            }
                                            td().text(performanceResults.getCurrent().avgTime().format()).end();
                                            for (String version : testHistory.getBaselineVersions()) {
                                                BaselineVersion baselineVersion = performanceResults.baseline(version);
                                                if (baselineVersion.getResults().isEmpty()) {
                                                    td().text("").end();
                                                } else {
                                                    td().text(baselineVersion.getResults().avgMemory().format()).end();
                                                }
                                            }
                                            td().text(performanceResults.getCurrent().avgMemory().format()).end();
                                        end();
                                    }
                                end();
                            }
                        end();
                        div().id("footer").text(String.format("Generated at %s by %s", format.format(new Date()), GradleVersion.current()));
                        end();
                    endAll();
                }};
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not generate performance test report to '%s'.", outputDirectory), e);
        }
    }
}
