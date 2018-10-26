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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.execution.CacheHandler;
import org.gradle.internal.execution.ExecutionException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.AsyncWorkTracker;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link TaskExecuter} which executes the actions of a task.
 */
public class ExecuteActionsTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(ExecuteActionsTaskExecuter.class);
    private final boolean buildCacheEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final TaskActionListener actionListener;
    private final WorkExecutor<UpToDateResult> workExecutor;

    public ExecuteActionsTaskExecuter(
        boolean buildCacheEnabled,
        BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker,
        TaskActionListener actionListener,
        WorkExecutor<UpToDateResult> workExecutor
    ) {
        this.buildCacheEnabled = buildCacheEnabled;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.actionListener = actionListener;
        this.workExecutor = workExecutor;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        final UpToDateResult result = workExecutor.execute(new TaskExecution(task, context));
        Throwable failure = result.getFailure();
        if (failure != null) {
            TaskExecutionException taskFailure = (failure instanceof ExecutionException)
                    ? new TaskExecutionException(task, failure.getCause())
                    : new TaskExecutionException(task, failure);
            state.setOutcome(taskFailure);
        } else {
            switch (result.getOutcome()) {
                case EXECUTED:
                    state.setOutcome(TaskExecutionOutcome.EXECUTED);
                    break;
                case UP_TO_DATE:
                    state.setOutcome(TaskExecutionOutcome.UP_TO_DATE);
                    break;
                case FROM_CACHE:
                    state.setOutcome(TaskExecutionOutcome.FROM_CACHE);
                    break;
                case NO_SOURCE:
                    state.setOutcome(TaskExecutionOutcome.NO_SOURCE);
                    break;
                default:
                    throw new AssertionError();
            }
        }
        context.setUpToDateMessages(result.getOutOfDateReasons());
        return new TaskExecuterResult() {
            @Override
            public OriginMetadata getOriginMetadata() {
                return result.getOriginMetadata();
            }
        };
    }

    private class TaskExecution implements UnitOfWork {
        private final TaskInternal task;
        private final TaskExecutionContext context;

        public TaskExecution(TaskInternal task, TaskExecutionContext context) {
            this.task = task;
            this.context = context;
        }

        @Override
        public String getIdentity() {
            return task.getPath();
        }

        @Override
        public boolean execute() {
            task.getState().setExecuting(true);
            try {
                LOGGER.debug("Executing actions for {}.", task);
                actionListener.beforeActions(task);
                executeActions(task, context);
                return task.getState().getDidWork();
            } finally {
                task.getState().setExecuting(false);
                actionListener.afterActions(task);
            }
        }

        @Override
        public void visitOutputs(OutputVisitor outputVisitor) {
            for (final TaskOutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                outputVisitor.visitOutput(property.getPropertyName(), property.getOutputType(), property.getPropertyFiles());
            }
        }

        @Override
        public void visitTrees(CacheableTreeVisitor visitor) {
            for (final TaskOutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                if (!(property instanceof CacheableTaskOutputFilePropertySpec)) {
                    throw new IllegalStateException("Non-cacheable property: " + property);
                }
                File cacheRoot = ((CacheableTaskOutputFilePropertySpec) property).getOutputFile();
                if (cacheRoot == null) {
                    continue;
                }

                visitor.visitTree(property.getPropertyName(), property.getOutputType(), cacheRoot);
            }
        }

        @Override
        public FileCollection getLocalState() {
            return context.getTaskProperties().getLocalStateFiles();
        }

        @Override
        public Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            return context.getTaskArtifactState().getExecutionStateChanges();
        }

        @Override
        public CacheHandler createCacheHandler() {
            return new CacheHandler() {
                @Override
                public <T> Optional<T> load(Function<BuildCacheKey, T> loader) {
                    // TODO Log this when creating the build cache key perhaps?
                    if (task.isHasCustomActions()) {
                        LOGGER.info("Custom actions are attached to {}.", task);
                    }
                    if (buildCacheEnabled
                            && context.isTaskCachingEnabled()
                            && context.getTaskArtifactState().isAllowedToUseCachedResults()
                            && context.getBuildCacheKey().isValid()
                    ) {
                        return Optional.ofNullable(loader.apply(context.getBuildCacheKey()));
                    } else {
                        return Optional.empty();
                    }
                }

                @Override
                public void store(Consumer<BuildCacheKey> storer) {
                    if (buildCacheEnabled
                            && context.isTaskCachingEnabled()
                            && context.getBuildCacheKey().isValid()
                    ) {
                        storer.accept(context.getBuildCacheKey());
                    }
                }
            };
        }

        @Override
        public void outputsRemovedAfterFailureToLoadFromCache() {
            context.getTaskArtifactState().afterOutputsRemovedBeforeTask();
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.ofNullable(task.getTimeout().getOrNull());
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            return context.getTaskArtifactState().snapshotAfterTaskExecution(context);
        }

        @Override
        public void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
            context.getTaskArtifactState().persistNewOutputs(finalOutputs, successful, originMetadata);
        }

        @Override
        public long markExecutionTime() {
            return context.markExecutionTime();
        }

        @Override
        public String getDisplayName() {
            return task.toString();
        }
    }

    private void executeActions(TaskInternal task, TaskExecutionContext context) {
        for (ContextAwareTaskAction action : new ArrayList<ContextAwareTaskAction>(task.getTaskActions())) {
            task.getState().setDidWork(true);
            task.getStandardOutputCapture().start();
            try {
                executeAction(action.getDisplayName(), task, action, context);
            } catch (StopActionException e) {
                // Ignore
                LOGGER.debug("Action stopped by some action with message: {}", e.getMessage());
            } catch (StopExecutionException e) {
                LOGGER.info("Execution stopped by some action with message: {}", e.getMessage());
                break;
            } finally {
                task.getStandardOutputCapture().stop();
            }
        }
    }

    private void executeAction(final String actionDisplayName, final TaskInternal task, final ContextAwareTaskAction action, TaskExecutionContext context) {
        action.contextualise(context);
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(actionDisplayName + " for " + task.getIdentityPath().getPath()).name(actionDisplayName);
            }

            @Override
            public void run(BuildOperationContext context) {
                BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
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
        public MultipleTaskActionFailures(String message, Iterable<? extends Throwable> causes) {
            super(message, causes);
        }
    }
}
