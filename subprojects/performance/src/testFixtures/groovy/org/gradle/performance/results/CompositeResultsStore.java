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

import java.util.*;

public class CompositeResultsStore implements ResultsStore {
    private final List<ResultsStore> stores;
    private Map<String, ResultsStore> tests;

    public CompositeResultsStore(ResultsStore... stores) {
        this.stores = Arrays.asList(stores);
    }

    @Override
    public List<String> getTestNames() {
        buildTests();
        return new ArrayList<String>(tests.keySet());
    }

    @Override
    public PerformanceTestHistory getTestResults(String testName) {
        return getStoreForTest(testName).getTestResults(testName);
    }

    @Override
    public PerformanceTestHistory getTestResults(String testName, int mostRecentN) {
        return getStoreForTest(testName).getTestResults(testName, mostRecentN);
    }

    private ResultsStore getStoreForTest(String testName) {
        buildTests();
        if (!tests.containsKey(testName)) {
            throw new IllegalArgumentException(String.format("Unknown test '%s'.", testName));
        }
        return tests.get(testName);
    }

    private void buildTests() {
        if (tests == null) {
            Map<String, ResultsStore> tests = new LinkedHashMap<String, ResultsStore>();
            for (ResultsStore store : stores) {
                for (String testName : store.getTestNames()) {
                    if (tests.containsKey(testName)) {
                        throw new IllegalArgumentException(String.format("Duplicate test '%s'", testName));
                    }
                    tests.put(testName, store);
                }
            }
            this.tests = tests;
        }
    }

}
