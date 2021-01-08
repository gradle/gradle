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
import org.gradle.performance.results.PerformanceExperiment;
import org.gradle.performance.results.PerformanceScenario;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.math.BigDecimal;
import java.util.Map;

import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.BIG_FLAKY_REGRESSION;
import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.SMALL_FLAKY_REGRESSION;
import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.ScenarioRegressionResult.STABLE_REGRESSION;

public class DefaultPerformanceFlakinessDataProvider implements PerformanceFlakinessDataProvider {
    private final Map<PerformanceExperiment, BigDecimal> flakinessRates;
    private final Map<PerformanceExperiment, BigDecimal> failureThresholds;

    public DefaultPerformanceFlakinessDataProvider(CrossVersionResultsStore crossVersionResultsStore) {
        flakinessRates = crossVersionResultsStore.getFlakinessRates();
        failureThresholds = crossVersionResultsStore.getFailureThresholds();
    }

    @Override
    public BigDecimal getFlakinessRate(PerformanceExperiment experiment) {
        BigDecimal flakinessRate = flakinessRates.get(experiment);
        return flakinessRate != null
            ? flakinessRate
            : flakinessRates.get(experimentWithTestClassRemoved(experiment));
    }

    private PerformanceExperiment experimentWithTestClassRemoved(PerformanceExperiment experiment) {
        // We query the old flakiness data before storing the test class in the performance DB
        // until more recent data is available. Can be removed in November 2020.
        return new PerformanceExperiment(experiment.getTestProject(), new PerformanceScenario(null, experiment.getScenario().getTestName()));
    }

    @Override
    public BigDecimal getFailureThreshold(PerformanceExperiment experiment) {
        BigDecimal failureThreshold = failureThresholds.get(experiment);
        return failureThreshold != null
            ? failureThreshold
            : failureThresholds.get(experimentWithTestClassRemoved(experiment));
    }

    @Override
    public ScenarioRegressionResult getScenarioRegressionResult(ScenarioBuildResultData scenario) {
        return getScenarioRegressionResult(scenario.getPerformanceExperiment(), scenario.getDifferencePercentage());
    }

    private ScenarioRegressionResult getScenarioRegressionResult(PerformanceExperiment experiment, double regressionPercentage) {
        if (isStableScenario(experiment)) {
            return STABLE_REGRESSION;
        }
        if (isBigRegression(experiment, regressionPercentage)) {
            return BIG_FLAKY_REGRESSION;
        }
        return SMALL_FLAKY_REGRESSION;
    }

    private boolean isBigRegression(PerformanceExperiment experiment, double regressionPercentage) {
        BigDecimal threshold = getFailureThreshold(experiment);
        return threshold != null && regressionPercentage / 100 > threshold.doubleValue();
    }

    private boolean isStableScenario(PerformanceExperiment experiment) {
        BigDecimal flakinessRate = getFlakinessRate(experiment);
        return flakinessRate == null || flakinessRate.doubleValue() < PerformanceFlakinessDataProvider.FLAKY_THRESHOLD;
    }
}
