/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.TaskExecutionRequest;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;
import java.util.Set;

/**
 * Computing the task graph based on the inputs and build configuration.
 *
 * @since 4.0
 */
public final class CalculateTaskGraphBuildOperationType implements BuildOperationType<CalculateTaskGraphBuildOperationType.Details, CalculateTaskGraphBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        List<TaskExecutionRequest> getTaskRequests();

        Set<String> getExcludedTaskNames();

    }

    @UsedByScanPlugin
    public interface Result {

        Set<String> getRequestedTaskPaths();

        Set<String> getExcludedTaskPaths();

    }

    public static class DetailsImpl implements Details {

        private final List<TaskExecutionRequest> taskRequests;
        private final Set<String> excludedTaskNames;

        public DetailsImpl(List<TaskExecutionRequest> taskRequests, Set<String> excludedTaskNames) {
            this.taskRequests = taskRequests;
            this.excludedTaskNames = excludedTaskNames;
        }

        public List<TaskExecutionRequest> getTaskRequests() {
            return taskRequests;
        }

        public Set<String> getExcludedTaskNames() {
            return excludedTaskNames;
        }

    }

    public static class ResultImpl implements Result {

        private final Set<String> requestedTaskPaths;
        private final Set<String> excludedTaskPaths;

        public ResultImpl(Set<String> requestedTaskPaths, Set<String> excludedTaskPaths) {
            this.requestedTaskPaths = requestedTaskPaths;
            this.excludedTaskPaths = excludedTaskPaths;
        }

        @Override
        public Set<String> getRequestedTaskPaths() {
            return requestedTaskPaths;
        }

        @Override
        public Set<String> getExcludedTaskPaths() {
            return excludedTaskPaths;
        }

    }

    private CalculateTaskGraphBuildOperationType() {
    }
}
