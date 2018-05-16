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
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import java.util.Collection;

/**
 * A {@link org.gradle.execution.BuildExecutionAction} that disables all selected tasks before they are executed.
 */
public class DryRunBuildExecutionAction implements BuildExecutionAction {
    private final StyledTextOutputFactory textOutputFactory;

    public DryRunBuildExecutionAction(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void execute(BuildExecutionContext context, Collection<? super Throwable> taskFailures) {
        GradleInternal gradle = context.getGradle();
        if (gradle.getStartParameter().isDryRun()) {
            for (Task task : gradle.getTaskGraph().getAllTasks()) {
                textOutputFactory.create(DryRunBuildExecutionAction.class)
                    .append(((TaskInternal) task).getIdentityPath().getPath())
                    .append(" ")
                    .style(StyledTextOutput.Style.ProgressStatus)
                    .append("SKIPPED")
                    .println();
            }
        } else {
            context.proceed();
        }
    }
}
