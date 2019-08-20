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
import java.util.Map;

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
}
