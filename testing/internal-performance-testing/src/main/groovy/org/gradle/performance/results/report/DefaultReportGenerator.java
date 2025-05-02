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

import org.gradle.performance.results.AllResultsStore;
import org.gradle.performance.results.CrossVersionResultsStore;
import org.gradle.performance.results.DefaultPerformanceFlakinessDataProvider;
import org.gradle.performance.results.PerformanceDatabase;
import org.gradle.performance.results.PerformanceFlakinessDataProvider;
import org.gradle.performance.results.ResultsStoreHelper;

import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.performance.results.PerformanceFlakinessDataProvider.ScenarioRegressionResult.BIG_FLAKY_REGRESSION;
import static org.gradle.performance.results.PerformanceFlakinessDataProvider.ScenarioRegressionResult.STABLE_REGRESSION;

// See more details in https://docs.google.com/document/d/1pghuxbCR5oYWhUrIK2e4bmABQt3NEIYOOIK4iHyjWyQ/edit#heading=h.is4fzcbmxxld
public class DefaultReportGenerator extends AbstractReportGenerator<AllResultsStore> {
    public static void main(String[] args) {
        new DefaultReportGenerator().generateReport(args);
    }

    @Override
    protected PerformanceFlakinessDataProvider getFlakinessDataProvider() {
        if (PerformanceDatabase.isAvailable()) {
            try (CrossVersionResultsStore resultsStore = new CrossVersionResultsStore()) {
                return new DefaultPerformanceFlakinessDataProvider(resultsStore, ResultsStoreHelper.determineOsFromChannel());
            }
        } else {
            return super.getFlakinessDataProvider();
        }
    }

    @Override
    protected void collectFailures(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, FailureCollector failureCollector) {
        executionDataProvider.getReportScenarios()
            .forEach(scenario -> {
                if (scenario.isBuildFailed()) {
                    failureCollector.scenarioFailed();
                } else if (scenario.isRegressed()) {
                    Set<PerformanceFlakinessDataProvider.ScenarioRegressionResult> regressionResults = scenario.getCurrentExecutions().stream()
                        .map(execution -> flakinessDataProvider.getScenarioRegressionResult(scenario.getPerformanceExperiment(), execution))
                        .collect(Collectors.toSet());
                    if (regressionResults.contains(STABLE_REGRESSION)) {
                        failureCollector.scenarioRegressed();
                    } else if (regressionResults.stream().allMatch(BIG_FLAKY_REGRESSION::equals)) {
                        failureCollector.flakyScenarioWithBigRegression();
                    } else {
                        failureCollector.flakyScenarioWithSmallRegression();
                    }
                }
            });
    }
}
