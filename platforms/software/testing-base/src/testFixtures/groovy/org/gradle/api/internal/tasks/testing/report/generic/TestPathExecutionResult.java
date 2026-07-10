/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Represents the result of executing a test path.
 *
 * <p>
 * A "root" is a single set of results, e.g. one {@code Test} task execution gives one root.
 * There may be multiple roots when tests are aggregated.
 * </p>
 * <p>
 * A "run" is a single execution of a test node. There may be multiple runs for a given root, e.g. when a test is retried.
 * They also may occur if names clash within a root, e.g. if two test methods have the same name for some reason.
 * </p>
 */
public interface TestPathExecutionResult {
    /**
     * Assert that a single root and run is present, and provides access to the results of the test path execution.
     */
    TestPathRootExecutionResult onlyRoot();

    /**
     * Assert that there is a single root and the given run number, and provides access to the results of the test path execution.
     */
    TestPathRootExecutionResult singleRootWithRun(int runNumber);

    /**
     * Assert that there is a root with the given root name, and a single run, and provides access to the results of the test path execution.
     */
    TestPathRootExecutionResult root(String rootName);

    /**
     * Assert that there is a root with the given root name and run number, and provides access to the results of the test path execution.
     */
    TestPathRootExecutionResult rootAndRun(String rootName, int runNumber);

    /**
     * Returns the names of the roots that were executed.
     */
    List<String> getRootNames();

    /**
     * Assert there is a single root and get the number of runs for it.
     */
    default int getSingleRootRunCount() {
        List<String> rootNames = getRootNames();
        assertThat("Expected a single root, but found: " + rootNames, rootNames.size() == 1);
        return getRunCount(rootNames.get(0));
    }

    /**
     * Returns the number of runs for the given root name.
     */
    int getRunCount(String rootName);
}
