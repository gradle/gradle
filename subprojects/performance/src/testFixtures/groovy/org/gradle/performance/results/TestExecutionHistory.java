/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.performance.fixture.PerformanceResults;

import java.util.List;

public class TestExecutionHistory {
    private final String name;
    private final List<String> versions;
    private final List<PerformanceResults> newestFirst;
    private final List<PerformanceResults> oldestFirst;

    public TestExecutionHistory(String name, List<String> versions, List<PerformanceResults> newestFirst, List<PerformanceResults> oldestFirst) {
        this.name = name;
        this.versions = versions;
        this.newestFirst = newestFirst;
        this.oldestFirst = oldestFirst;
    }

    public String getName() {
        return name;
    }

    public List<String> getBaselineVersions() {
        return versions;
    }

    /**
     * Returns results from most recent to least recent.
     */
    public List<PerformanceResults> getResults() {
        return newestFirst;
    }

    /**
     * Returns results from least recent to most recent.
     */
    public List<PerformanceResults> getResultsOldestFirst() {
        return oldestFirst;
    }
}
