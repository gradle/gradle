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

import java.util.List;
import java.util.Set;

/*
 * Operation Descriptor for Calculating the task graph of a build.
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public class CalculateTaskGraphDescriptor {
    private final List<TaskExecutionRequest> taskRequests;
    private final Set<String> excludedTaskNames;

    public CalculateTaskGraphDescriptor(List<TaskExecutionRequest> taskRequests, Set<String> excludedTaskNames) {
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
