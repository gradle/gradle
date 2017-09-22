/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.work.AsyncWorkTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link org.gradle.api.internal.tasks.TaskExecuter} which executes the actions of a task.
 */
public class ExecuteActionsTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(ExecuteActionsTaskExecuter.class);
    private final TaskOutputsGenerationListener outputsGenerationListener;
    private final TaskActionListener listener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;

    public ExecuteActionsTaskExecuter(TaskOutputsGenerationListener outputsGenerationListener, TaskActionListener taskActionListener, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker asyncWorkTracker) {
        this.outputsGenerationListener = outputsGenerationListener;
        this.listener = taskActionListener;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        listener.beforeActions(task);
        if (!task.getTaskActions().isEmpty()) {
            outputsGenerationListener.beforeTaskOutputsGenerated();
        }
        state.setExecuting(true);
        try {
            GradleException failure = executeActions(task, state, context);
            if (failure != null) {
                state.setOutcome(failure);
            } else {
                state.setOutcome(
                    state.getDidWork() ? TaskExecutionOutcome.EXECUTED : TaskExecutionOutcome.UP_TO_DATE
                );
            }
            context.getTaskArtifactState().snapshotAfterTaskExecution(failure);
        } finally {
            state.setExecuting(false);
            listener.afterActions(task);
        }
    }

    private GradleException executeActions(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.debug("Executing actions for {}.", task);
        final List<ContextAwareTaskAction> actions = new ArrayList<ContextAwareTaskAction>(task.getTaskActions());
        for (ContextAwareTaskAction action : actions) {
            state.setDidWork(true);
            task.getStandardOutputCapture().start();
            try {
                executeAction(action.getDisplayName(), task, action, context);
            } catch (StopActionException e) {
                // Ignore
                LOGGER.debug("Action stopped by some action with message: {}", e.getMessage());
            } catch (StopExecutionException e) {
                LOGGER.info("Execution stopped by some action with message: {}", e.getMessage());
                break;
            } catch (Throwable t) {
                return new TaskExecutionException(task, t);
            } finally {
                task.getStandardOutputCapture().stop();
            }
        }
        return null;
    }

    private void executeAction(final String actionDisplayName, final TaskInternal task, final ContextAwareTaskAction action, TaskExecutionContext context) {
        action.contextualise(context);
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(actionDisplayName + " for " + task.getPath()).name(actionDisplayName);
            }

            @Override
            public void run(BuildOperationContext context) {
                BuildOperationState currentOperation = buildOperationExecutor.getCurrentOperation();
                Throwable actionFailure = null;
                try {
                    action.execute(task);
                } catch (Throwable t) {
                    actionFailure = t;
                } finally {
                    action.releaseContext();
                }

                try {
                    asyncWorkTracker.waitForCompletion(currentOperation, true);
                } catch (Throwable t) {
                    List<Throwable> failures = Lists.newArrayList();

                    if (actionFailure != null) {
                        failures.add(actionFailure);
                    }

                    if (t instanceof MultiCauseException) {
                        failures.addAll(((MultiCauseException) t).getCauses());
                    } else {
                        failures.add(t);
                    }

                    if (failures.size() > 1) {
                        throw new MultipleTaskActionFailures("Multiple task action failures occurred:", failures);
                    } else {
                        throw UncheckedException.throwAsUncheckedException(failures.get(0));
                    }
                }

                if (actionFailure != null) {
                    throw UncheckedException.throwAsUncheckedException(actionFailure);
                }
            }
        });
    }

    @Contextual
    private static class MultipleTaskActionFailures extends DefaultMultiCauseException {
        public MultipleTaskActionFailures(String message, Throwable... causes) {
            super(message, causes);
        }

        public MultipleTaskActionFailures(String message, Iterable<? extends Throwable> causes) {
            super(message, causes);
        }
    }
}
