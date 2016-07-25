package org.gradle.performance.fixture;

import com.google.common.base.Splitter;

import java.util.List;

/**
 * Determines whether a specific scenario within a performance test should run.
 */
public class TestScenarioSelector {
    public boolean shouldRun(String testId) {
        String scenarioProperty = System.getProperty("org.gradle.performance.scenarios", "");
        List<String> scenarios = Splitter.on(",").omitEmptyStrings().splitToList(scenarioProperty);
        return scenarios.isEmpty() || scenarios.contains(testId);
    }
}
