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

package org.gradle.initialization;

import java.util.List;

public class BuildInstantReplay {
    private final List<String> requestedTaskSelectors;
    private final List<TestFailure> testFailures;

    BuildInstantReplay(List<String> requestedTaskSelectors, List<TestFailure> testFailures) {
        this.requestedTaskSelectors = requestedTaskSelectors;
        this.testFailures = testFailures;
    }

    public List<String> getRequestedTaskSelectors() {
        return requestedTaskSelectors;
    }

    public List<TestFailure> getTestFailures() {
        return testFailures;
    }
//    List<String> excludedTasks;
//    int numberOfWorkers;

    public static class TestFailure {
        private final String taskPath;
        private final String failedTest;

        TestFailure(String taskPath, String failedTest) {
            this.taskPath = taskPath;
            this.failedTest = failedTest;
        }

        public String getTaskPath() {
            return taskPath;
        }

        public String getFailedTest() {
            return failedTest;
        }
    }
}
