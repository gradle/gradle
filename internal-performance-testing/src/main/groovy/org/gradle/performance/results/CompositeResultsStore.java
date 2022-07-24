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

import org.gradle.internal.concurrent.CompositeStoppable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompositeResultsStore implements ResultsStore {
    private final List<ResultsStore> stores;
    private Map<PerformanceExperiment, ResultsStore> tests;

    public CompositeResultsStore(ResultsStore... stores) {
        this.stores = Arrays.asList(stores);
    }

    @Override
    public List<PerformanceExperiment> getPerformanceExperiments() {
        buildTests();
        return new ArrayList<>(tests.keySet());
    }

    @Override
    public PerformanceTestHistory getTestResults(PerformanceExperiment experiment, int mostRecentN, int maxDaysOld, List<String> channelPatterns, List<String> teamcityBuildIds) {
        return getStoreForTest(experiment).getTestResults(experiment, mostRecentN, maxDaysOld, channelPatterns, teamcityBuildIds);
    }

    @Override
    public Map<PerformanceExperimentOnOs, Long> getEstimatedExperimentDurationsInMillis() {
        return stores.stream()
            .flatMap(store -> store.getEstimatedExperimentDurationsInMillis().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Math::max, LinkedHashMap::new));
    }

    private ResultsStore getStoreForTest(PerformanceExperiment experiment) {
        buildTests();
        if (!tests.containsKey(experiment)) {
            throw new IllegalArgumentException(String.format("Unknown test '%s'.", experiment));
        }
        return tests.get(experiment);
    }

    private void buildTests() {
        if (tests == null) {
            Map<PerformanceExperiment, ResultsStore> tests = new LinkedHashMap<>();
            for (ResultsStore store : stores) {
                for (PerformanceExperiment experiment : store.getPerformanceExperiments()) {
                    if (tests.containsKey(experiment)) {
                        throw new IllegalArgumentException(String.format("Duplicate test '%s'", experiment));
                    }
                    tests.put(experiment, store);
                }
            }
            this.tests = tests;
        }
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(stores).stop();
    }
}
