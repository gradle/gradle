/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import com.google.common.collect.Sets;
import org.gradle.performance.results.PerformanceFlakinessDataProvider;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.ResultsStore;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.performance.results.report.FlakinessDetectionPerformanceExecutionDataProvider.isFlaky;
import static org.gradle.performance.results.report.Tag.FixedTag.FLAKY;

public class FlakinessIndexPageGenerator extends AbstractTablePageGenerator {
    public FlakinessIndexPageGenerator(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider dataProvider) {
        super(flakinessDataProvider, dataProvider);
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new TableHtml(writer) {
            @Override
            protected String getPageTitle() {
                return "Flakiness report for commit " + executionDataProvider.getCommitId();
            }

            @Override
            protected String getTableTitle() {
                int total = executionDataProvider.getReportScenarios().size();
                long flakyCount = executionDataProvider.getReportScenarios().stream().filter(FlakinessDetectionPerformanceExecutionDataProvider::isFlaky).count();
                return "Scenarios ( total: " + total + ", flaky: " + flakyCount + ")";
            }

            @Override
            protected boolean renderFailureSelectButton() {
                return false;
            }

            @Override
            protected List<PerformanceReportScenario> getCrossVersionScenarios() {
                return new ArrayList<>(executionDataProvider.getReportScenarios());
            }

            @Override
            protected List<PerformanceReportScenario> getCrossBuildScenarios() {
                return Collections.emptyList();
            }

            @Override
            protected String determineScenarioBackgroundColorCss(PerformanceReportScenario scenario) {
                return isFlaky(scenario) ? "alert-warning" : "alert-info";
            }

            @Override
            protected Set<Tag> determineTags(PerformanceReportScenario scenario) {
                return isFlaky(scenario) ? Sets.newHashSet(FLAKY) : Collections.emptySet();
            }

            @Override
            protected void renderScenarioButtons(int index, PerformanceReportScenario scenario) {
            }
        };
    }

}
