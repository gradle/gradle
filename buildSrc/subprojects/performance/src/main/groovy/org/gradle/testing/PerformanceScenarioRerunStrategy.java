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
 * Strategy for when to rerun a performance scenario
 */
interface PerformanceScenarioRerunStrategy {

    /**
     * Whether the scenario should rerun
     *
     * @param scenarioRunCount the number of times the scenario ran already, including this execution
     * @param successful whether the current run of the scenario was successful (i.e. didn't fail)
     * @return whether the scenario should rerun
     */
    boolean shouldRerun(int scenarioRunCount, boolean successful);

    /**
     * Never rerun scenarios
     */
    // Not using a lambda since we can't track the implementation of it
    @SuppressWarnings("Convert2Lambda")
    PerformanceScenarioRerunStrategy NEVER = new PerformanceScenarioRerunStrategy() {
        @Override
        public boolean shouldRerun(int scenarioRunCount, boolean successful) {
            return false;
        }
    };
}
