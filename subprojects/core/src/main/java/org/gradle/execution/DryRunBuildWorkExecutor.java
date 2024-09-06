/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.operations.BuildOperationRunner;

/**
 * A {@link BuildWorkExecutor} that disables all selected tasks before they are executed.
 */
public class DryRunBuildWorkExecutor implements BuildWorkExecutor {
    private final BuildOperationRunner buildOperationRunner;
    private final BuildWorkExecutor delegate;

    public DryRunBuildWorkExecutor(BuildOperationRunner buildOperationRunner, BuildWorkExecutor delegate) {
        this.buildOperationRunner = buildOperationRunner;
        this.delegate = delegate;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        if (gradle.getStartParameter().isDryRun()) {
            // Using verbose to show the task headers
            gradle.getStartParameter().setConsoleOutput(ConsoleOutput.Verbose);
            plan.getContents().getScheduledNodes().visitNodes((nodes, __) -> {
                for (Node node : nodes) {
                    node.dryRun(buildOperationRunner);
                }
            });
            return ExecutionResult.succeeded();
        } else {
            return delegate.execute(gradle, plan);
        }
    }
}
