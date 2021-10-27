/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.ExecutionResult;

public class DefaultBuildTreeWorkExecutor implements BuildTreeWorkExecutor {
    private final IncludedBuildTaskGraph includedBuildTaskGraph;
    private final BuildLifecycleController buildController;

    public DefaultBuildTreeWorkExecutor(IncludedBuildTaskGraph includedBuildTaskGraph, BuildLifecycleController buildController) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
        this.buildController = buildController;
    }

    @Override
    public ExecutionResult<Void> execute() {
        includedBuildTaskGraph.startTaskExecution();
        ExecutionResult<Void> buildResult = buildController.executeTasks();
        ExecutionResult<Void> includedBuildsResult = includedBuildTaskGraph.awaitTaskCompletion();
        return buildResult.withFailures(includedBuildsResult);
    }
}
