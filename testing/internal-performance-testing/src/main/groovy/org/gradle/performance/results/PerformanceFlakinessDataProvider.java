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

package org.gradle.performance.results;

import java.math.BigDecimal;

public interface PerformanceFlakinessDataProvider {
    /**
     * For the scenario whose flakiness rate is higher than 5%, we see it as "flaky scenario"
     */
    double FLAKY_THRESHOLD = 0.05;

    /**
     * Flakiness rate of a scenario is the number of times the scenario had a regression of an improvement with more than 99.9%
     * in the flakiness detection builds divided by the total number of runs of the scenario.
     *
     * <pre>
     *  SELECT TESTID, TESTPROJECT, AVG(CONVERT(CASEWHEN(DIFFCONFIDENCE &gt; 0.97, 1, 0), DECIMAL)) AS FAILURE_RATE,
     *  FROM TESTEXECUTION
     *  WHERE (CHANNEL = 'flakiness-detection-master' OR CHANNEL = 'flakiness-detection-release')
     *  GROUP BY TESTID
     * </pre>
     *
     * @return the flakiness rate in DB, null if not exists.
     */
    BigDecimal getFlakinessRate(PerformanceExperiment experiment);

    /**
     * The failure threshold of flaky scenario, if a flaky scenario performance test's difference is higher than this value,
     * it will be recognized as a real failure.
     *
     * <pre>
     *  SELECT TESTID, TESTPROJECT, MAX(ABS((BASELINEMEDIAN-CURRENTMEDIAN)/BASELINEMEDIAN)) as THRESHOLD
     *  FROM TESTEXECUTION
     *  WHERE (CHANNEL = 'flakiness-detection-master' or CHANNEL= 'flakiness-detection-release') AND DIFFCONFIDENCE &gt; 0.99
     *  GROUP BY TESTID
     * </pre>
     *
     * @return the failure threshold in DB, null if not exists.
     */
    BigDecimal getFailureThreshold(PerformanceExperiment experiment);

    ScenarioRegressionResult getScenarioRegressionResult(PerformanceExperiment experiment, PerformanceReportScenarioHistoryExecution execution);

    enum ScenarioRegressionResult {
        STABLE_REGRESSION(true),
        SMALL_FLAKY_REGRESSION(false),
        BIG_FLAKY_REGRESSION(true);

        private final boolean failsBuild;

        ScenarioRegressionResult(boolean failsBuild) {
            this.failsBuild = failsBuild;
        }

        public boolean isFailsBuild() {
            return failsBuild;
        }
    }

    enum EmptyPerformanceFlakinessDataProvider implements PerformanceFlakinessDataProvider {
        INSTANCE;

        @Override
        public BigDecimal getFlakinessRate(PerformanceExperiment experiment) {
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal getFailureThreshold(PerformanceExperiment experiment) {
            return BigDecimal.ZERO;
        }

        @Override
        public ScenarioRegressionResult getScenarioRegressionResult(PerformanceExperiment experiment, PerformanceReportScenarioHistoryExecution execution) {
            return ScenarioRegressionResult.STABLE_REGRESSION;
        }
    }
}
