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

package org.gradle.testing;

/**
 * Rerun failed scenarios.
 *
 * The scenarios are only retried if fewer than {@link #MAX_RETRIED_SCENARIOS} failed in total.
 */
class RetryFailedRerunStrategy implements PerformanceScenarioRerunStrategy {
    private static final int MAX_RETRIED_SCENARIOS = 15;

    private int totalRetries;
    private final int maxRetryCount;

    /**
     * @param maxRetryCount maximum number of retries for a scenario.
     */
    public RetryFailedRerunStrategy(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    @Override
    public boolean shouldRerun(int scenarioRunCount, boolean successful) {
        boolean shouldRerun = !successful && scenarioRunCount <= maxRetryCount && totalRetries < MAX_RETRIED_SCENARIOS;
        if (shouldRerun) {
            totalRetries++;
        }
        return shouldRerun;
    }
}
