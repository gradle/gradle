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
    List<String> versions;
    List<PerformanceResults> results;

    public TestExecutionHistory(List<String> versions, List<PerformanceResults> results) {
        this.versions = versions;
        this.results = results;
    }

    public List<String> getBaselineVersions() {
        return versions;
    }

    public List<PerformanceResults> getResults() {
        return results;
    }
}
