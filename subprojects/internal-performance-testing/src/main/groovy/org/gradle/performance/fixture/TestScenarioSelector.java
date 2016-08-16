/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Determines whether a specific scenario within a performance test should run and whether it should run locally
 * or be added to the list of scenarios to run in distributed mode.
 */
public class TestScenarioSelector {

    public boolean shouldRun(String testId, Set<String> templates, ResultsStore resultsStore) {
        if (testId.contains(";")) {
            throw new IllegalArgumentException("Test ID cannot contain ';', but was '" + testId + "'");
        }
        String scenarioProperty = System.getProperty("org.gradle.performance.scenarios", "");
        List<String> scenarios = Splitter.on(";").omitEmptyStrings().splitToList(scenarioProperty);
        boolean shouldRun = scenarios.isEmpty() || scenarios.contains(testId);
        String scenarioList = System.getProperty("org.gradle.performance.scenario.list");
        if (shouldRun && scenarioList != null) {
            addToScenarioList(testId, templates, new File(scenarioList), resultsStore);
            return false;
        } else {
            return shouldRun;
        }
    }

    private void addToScenarioList(String testId, Set<String> templates, File scenarioList, ResultsStore resultsStore) {
        try {
            long estimatedRuntime = getEstimatedRuntime(testId, resultsStore);
            List<String> args = Lists.newArrayList();
            args.add(testId);
            args.add(String.valueOf(estimatedRuntime));
            args.addAll(templates);
            Files.touch(scenarioList);
            Files.append(Joiner.on(';').join(args) + '\n', scenarioList, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write to scenario list at " + scenarioList, e);
        }
    }

    private long getEstimatedRuntime(String testId, ResultsStore resultsStore) {
        String channel = ResultsStoreHelper.determineChannel();
        PerformanceTestHistory history = resultsStore.getTestResults(testId, 1, 365, channel);
        PerformanceTestExecution lastRun = Iterables.getFirst(history.getExecutions(), null);
        if (lastRun == null) {
            return 0;
        } else {
            return lastRun.getEndTime() - lastRun.getStartTime();
        }
    }
}
