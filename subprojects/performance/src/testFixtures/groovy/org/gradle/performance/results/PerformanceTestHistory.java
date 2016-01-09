/*
 * Copyright 2014 the original author or authors.
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

import java.util.List;

/**
 * The execution history for a particular performance test.
 */
public interface PerformanceTestHistory {
    /**
     * A unique id for this performance test, must be stable over time.
     */
    String getId();

    /**
     * A human consumable display name for this performance test.
     */
    String getDisplayName();

    /**
     * The results of all executions of this performance test, ordered from most recent to least recent.
     */
    List<? extends PerformanceTestExecution> getExecutions();

    /**
     * Returns the number of scenarios for this performance test.
     */
    int getScenarioCount();

    /**
     * Returns the display names for the scenarios of this performance test.
     */
    List<String> getScenarioLabels();

    /**
     * Returns the scenarios that are executed for this performance test.
     */
    List<? extends ScenarioDefinition> getScenarios();
}
