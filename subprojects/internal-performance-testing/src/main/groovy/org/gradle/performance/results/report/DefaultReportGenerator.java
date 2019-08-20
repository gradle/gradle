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
import org.gradle.performance.results.ScenarioBuildResultData;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// See more details in https://docs.google.com/document/d/1pghuxbCR5oYWhUrIK2e4bmABQt3NEIYOOIK4iHyjWyQ/edit#heading=h.is4fzcbmxxld
public class DefaultReportGenerator extends AbstractReportGenerator<AllResultsStore> {
    public static void main(String[] args) {
        new DefaultReportGenerator().generateReport(args);
    }

    @Override
    protected PerformanceFlakinessDataProvider getFlakinessDataProvider() {
        try (CrossVersionResultsStore resultsStore = new CrossVersionResultsStore()) {
            return new DefaultPerformanceFlakinessDataProvider(resultsStore);
        }
    }

    @Override
    protected void checkResult(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider) {
        AtomicInteger buildFailure = new AtomicInteger(0);
        AtomicInteger stableScenarioRegression = new AtomicInteger(0);
        AtomicInteger flakyScenarioSmallRegression = new AtomicInteger(0);
        AtomicInteger flakyScenarioBigRegression = new AtomicInteger(0);

        executionDataProvider.getScenarioExecutionData()
            .stream().collect(Collectors.groupingBy(ScenarioBuildResultData::getScenarioName))
            .forEach((scenario, results) -> {
                if (results.stream().anyMatch(ScenarioBuildResultData::isBuildFailed)) {
                    buildFailure.getAndIncrement();
                } else if (isStableScenario(flakinessDataProvider, scenario)) {
                    if (results.stream().allMatch(ScenarioBuildResultData::isRegressed)) {
                        stableScenarioRegression.getAndIncrement();
                    }
                } else if (results.stream().noneMatch(ScenarioBuildResultData::isSuccessful)) {
                    if (results.stream().anyMatch(scenarioResult -> isBigRegression(scenarioResult, flakinessDataProvider))) {
                        flakyScenarioBigRegression.getAndIncrement();
                    } else {
                        flakyScenarioSmallRegression.getAndIncrement();
                    }
                }
            });
        if (buildFailure.get() + stableScenarioRegression.get() + flakyScenarioBigRegression.get() != 0) {
            throw new GradleException(formatErroString(buildFailure.get(), stableScenarioRegression.get(), flakyScenarioBigRegression.get(), flakyScenarioSmallRegression.get()));
        }
        if (!executionDataProvider.getScenarioExecutionData().stream().allMatch(ScenarioBuildResultData::isSuccessful)) {
            markBuildAsSuccessful();
        }
    }

    private void markBuildAsSuccessful() {
        System.out.println("##teamcity[buildStatus status='SUCCESS' text='First run failed but the rerun succeeded.']");
    }

    private String formatErroString(int buildFailure, int stableScenarioRegression, int flakyScenarioBigRegression, int flakyScenarioSmallRegression) {
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

    private boolean isBigRegression(ScenarioBuildResultData scenarioResult, PerformanceFlakinessDataProvider flakinessDataProvider) {
        BigDecimal threshold = flakinessDataProvider.getFailureThreshold(scenarioResult.getScenarioName());
        return threshold != null && scenarioResult.getDifferencePercentage() / 100 > threshold.doubleValue();
    }

    private boolean isStableScenario(PerformanceFlakinessDataProvider flakinessDataProvider, String scenario) {
        BigDecimal flakinessRate = flakinessDataProvider.getFlakinessRate(scenario);
        return flakinessRate == null || flakinessRate.doubleValue() < PerformanceFlakinessDataProvider.FLAKY_THRESHOLD;
    }
}
