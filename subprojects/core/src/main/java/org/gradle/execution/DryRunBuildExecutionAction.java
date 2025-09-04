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

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.provider.ConfigurationTimeBarrier;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

/**
 * A {@link BuildWorkExecutor} that disables all selected tasks before they are executed.
 */
public class DryRunBuildExecutionAction implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final StyledTextOutputFactory textOutputFactory;
    private final ConfigurationTimeBarrier configurationTimeBarrier;

    public DryRunBuildExecutionAction(
        BuildWorkExecutor delegate,
        StyledTextOutputFactory textOutputFactory,
        ConfigurationTimeBarrier configurationTimeBarrier
    ) {
        this.delegate = delegate;
        this.textOutputFactory = textOutputFactory;
        this.configurationTimeBarrier = configurationTimeBarrier;
    }

    @Override
    public ExecutionResult<Void> execute(GradleInternal gradle, FinalizedExecutionPlan plan) {
        if (configurationTimeBarrier.isAtConfigurationTime()) {
            return delegate.execute(gradle, plan);
        }
        for (Task task : plan.getContents().getTasks()) {
            textOutputFactory.create(DryRunBuildExecutionAction.class)
                .append(((TaskInternal) task).getIdentityPath().asString())
                .append(" ")
                .style(StyledTextOutput.Style.ProgressStatus)
                .append("SKIPPED")
                .println();
        }
        return ExecutionResult.succeeded();
    }
}
