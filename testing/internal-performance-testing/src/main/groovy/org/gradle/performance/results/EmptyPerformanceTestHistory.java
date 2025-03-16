/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableList;

import java.util.List;

public class EmptyPerformanceTestHistory implements PerformanceTestHistory {

    private final PerformanceExperiment experiment;

    public EmptyPerformanceTestHistory(PerformanceExperiment experiment) {
        this.experiment = experiment;
    }

    @Override
    public PerformanceExperiment getExperiment() {
        return experiment;
    }

    @Override
    public List<? extends PerformanceTestExecution> getExecutions() {
        return ImmutableList.of();
    }

    @Override
    public int getScenarioCount() {
        return 0;
    }

    @Override
    public List<String> getScenarioLabels() {
        return ImmutableList.of();
    }

    @Override
    public List<? extends ScenarioDefinition> getScenarios() {
        return ImmutableList.of();
    }
}
