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

import org.gradle.performance.results.CrossVersionResultsStore;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.math.BigDecimal;
import java.util.Map;

import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.BIG_FLAKY_REGRESSION;
import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.SMALL_FLAKY_REGRESSION;
import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.STABLE_REGRESSION;

public class DefaultPerformanceFlakinessDataProvider implements PerformanceFlakinessDataProvider {
    private final Map<String, BigDecimal> flakinessRates;
    private final Map<String, BigDecimal> failureThresholds;

    public DefaultPerformanceFlakinessDataProvider(CrossVersionResultsStore crossVersionResultsStore) {
        flakinessRates = crossVersionResultsStore.getFlakinessRates();
        failureThresholds = crossVersionResultsStore.getFailureThresholds();
    }

    @Override
    public BigDecimal getFlakinessRate(String scenario) {
        return flakinessRates.get(scenario);
    }

    @Override
    public BigDecimal getFailureThreshold(String scenario) {
        return failureThresholds.get(scenario);
    }

    @Override
    public ScenarioRegressionResult getScenarioRegressionResult(ScenarioBuildResultData scenario) {
        return getScenarioRegressionResult(scenario.getScenarioName(), scenario.getDifferencePercentage());
    }

    private ScenarioRegressionResult getScenarioRegressionResult(String scenarioName, double regressionPercentage) {
        if (isStableScenario(scenarioName)) {
            return STABLE_REGRESSION;
        }
        if (isBigRegression(scenarioName, regressionPercentage)) {
            return BIG_FLAKY_REGRESSION;
        }
        return SMALL_FLAKY_REGRESSION;
    }

    private boolean isBigRegression(String scenarioName, double regressionPercentage) {
        BigDecimal threshold = getFailureThreshold(scenarioName);
        return threshold != null && regressionPercentage / 100 > threshold.doubleValue();
    }

    private boolean isStableScenario(String scenarioName) {
        BigDecimal flakinessRate = getFlakinessRate(scenarioName);
        return flakinessRate == null || flakinessRate.doubleValue() < PerformanceFlakinessDataProvider.FLAKY_THRESHOLD;
    }
}
