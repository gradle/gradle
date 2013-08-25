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

import com.google.common.base.Joiner;
import com.googlecode.jatl.Html;
import org.gradle.performance.fixture.BaselineVersion;
import org.gradle.performance.fixture.PerformanceResults;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

public class TestPageGenerator extends HtmlPageGenerator<TestExecutionHistory> {
    @Override
    public void render(final TestExecutionHistory testHistory, Writer writer) throws IOException {
        new Html(writer) {{
            html();
                head();
                    headSection(this);
                    title().text(String.format("Profile test %s report", testHistory.getName())).end();
                end();
                body();
                div().id("content");
                    h2().text(String.format("Test %s", testHistory.getName())).end();
                    table();
                        tr();
                            th().colspan("8").end();
                            th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average heap usage").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Test project").end();
                            th().text("Tasks").end();
                            th().text("Test version").end();
                            th().text("Operating System").end();
                            th().text("JVM").end();
                            th().text("Branch").end();
                            th().text("Commit").end();
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
                                td().text(performanceResults.getTestProject()).end();
                                td();
                                    text(Joiner.on(", ").join(performanceResults.getArgs()));
                                    text(" ");
                                    text(Joiner.on(", ").join(performanceResults.getTasks()));
                                end();
                                td().text(performanceResults.getVersionUnderTest()).end();
                                td().text(performanceResults.getOperatingSystem()).end();
                                td().text(performanceResults.getJvm()).end();
                                td().text(performanceResults.getVcsBranch()).end();
                                td().text(performanceResults.getVcsCommit()).end();
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
                end();
                footer(this);
            endAll();
        }};
    }
}
