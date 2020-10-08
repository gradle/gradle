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

import org.gradle.api.GradleException;
import org.gradle.performance.results.AllResultsStore;
import org.gradle.performance.results.CrossVersionResultsStore;
import org.gradle.performance.results.PerformanceDatabase;
import org.gradle.performance.results.PerformanceExperiment;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.BIG_FLAKY_REGRESSION;
import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.STABLE_REGRESSION;

// See more details in https://docs.google.com/document/d/1pghuxbCR5oYWhUrIK2e4bmABQt3NEIYOOIK4iHyjWyQ/edit#heading=h.is4fzcbmxxld
public class DefaultReportGenerator extends AbstractReportGenerator<AllResultsStore> {
    public static void main(String[] args) {
        new DefaultReportGenerator().generateReport(args);
    }

    @Override
    protected PerformanceFlakinessDataProvider getFlakinessDataProvider() {
        if (PerformanceDatabase.isAvailable()) {
            try (CrossVersionResultsStore resultsStore = new CrossVersionResultsStore()) {
                return new DefaultPerformanceFlakinessDataProvider(resultsStore);
            }
        } else {
            return super.getFlakinessDataProvider();
        }
    }

    @Override
    protected void checkResult(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider) {
        AtomicInteger buildFailure = new AtomicInteger(0);
        AtomicInteger stableScenarioRegression = new AtomicInteger(0);
        AtomicInteger flakyScenarioSmallRegression = new AtomicInteger(0);
        AtomicInteger flakyScenarioBigRegression = new AtomicInteger(0);

        executionDataProvider.getScenarioExecutionData()
            .forEach(scenario -> {
                if (scenario.getRawData().stream().allMatch(ScenarioBuildResultData::isBuildFailed)) {
                    buildFailure.getAndIncrement();
                } else if (scenario.getRawData().stream().allMatch(ScenarioBuildResultData::isRegressed)) {
                    Set<PerformanceFlakinessDataProvider.ScenarioRegressionResult> regressionResults = scenario.getRawData().stream()
                        .map(flakinessDataProvider::getScenarioRegressionResult)
                        .collect(Collectors.toSet());
                    if (regressionResults.contains(STABLE_REGRESSION)) {
                        stableScenarioRegression.getAndIncrement();
                    } else if (regressionResults.stream().allMatch(BIG_FLAKY_REGRESSION::equals)) {
                        flakyScenarioBigRegression.getAndIncrement();
                    } else {
                        flakyScenarioSmallRegression.getAndIncrement();
                    }
                }
            });
        boolean onlyOneTestExecuted = executionDataProvider.getScenarioExecutionData().size() == 1;
        String resultString = onlyOneTestExecuted
            ? formatSingleResultString(executionDataProvider, buildFailure.get(), stableScenarioRegression.get(), flakyScenarioBigRegression.get(), flakyScenarioSmallRegression.get())
            : formatResultString(buildFailure.get(), stableScenarioRegression.get(), flakyScenarioBigRegression.get(), flakyScenarioSmallRegression.get());
        if (buildFailure.get() + stableScenarioRegression.get() + flakyScenarioBigRegression.get() != 0) {
            String failedMessage = "Performance test failed" + resultString;
            if (onlyOneTestExecuted) {
                System.out.println("##teamcity[buildStatus text='" + failedMessage + "']");
            }
            throw new GradleException(failedMessage);
        }
        String successMessage = "Performance test passed" + resultString;
        System.out.println(successMessage);
        markBuildAsSuccessful(successMessage);
    }

    private void markBuildAsSuccessful(String successMessage) {
        System.out.println("##teamcity[buildStatus status='SUCCESS' text='" + successMessage + "']");
    }

    private String formatResultString(int buildFailure, int stableScenarioRegression, int flakyScenarioBigRegression, int flakyScenarioSmallRegression) {
        StringBuilder sb = new StringBuilder();
        if (buildFailure != 0) {
            sb.append(", ").append(buildFailure).append(" scenario(s) failed");
        }
        if (stableScenarioRegression != 0) {
            sb.append(", ").append(stableScenarioRegression).append(" stable scenario(s) regressed");
        }
        if (flakyScenarioBigRegression != 0) {
            sb.append(", ").append(flakyScenarioBigRegression).append(" flaky scenario(s) regressed badly");
        }

        if (flakyScenarioSmallRegression != 0) {
            sb.append(", ").append(flakyScenarioSmallRegression).append(" flaky scenarios(s) regressed slightly");
        }

        sb.append('.');
        return sb.toString();
    }

    private String formatSingleResultString(PerformanceExecutionDataProvider executionDataProvider, int buildFailure, int stableScenarioRegression, int flakyScenarioBigRegression, int flakyScenarioSmallRegression) {
        PerformanceExperiment performanceExperiment = executionDataProvider.getScenarioExecutionData().first().getPerformanceExperiment();
        String messageTemplate;
        if (buildFailure != 0) {
            messageTemplate = ", scenario %s failed.";
        } else if (stableScenarioRegression != 0) {
            messageTemplate = ", stable scenario %s regressed.";
        } else if (flakyScenarioBigRegression != 0) {
            messageTemplate = ", flaky scenario %s regressed badly.";
        } else if (flakyScenarioSmallRegression != 0) {
            messageTemplate = ", flaky scenario %s regressed slightly.";
        } else {
            messageTemplate = ", scenario %s succeeded.";
        }
        return String.format(messageTemplate, performanceExperiment.getDisplayName());
    }
}
