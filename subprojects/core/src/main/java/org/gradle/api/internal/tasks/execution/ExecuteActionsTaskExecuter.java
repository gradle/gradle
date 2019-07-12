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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.IncrementalInputsTaskAction;
import org.gradle.api.internal.project.taskfactory.IncrementalTaskInputsTaskAction;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.CacheableOutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.execution.AfterPreviousExecutionContext;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.impl.OutputFilterUtil;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.ExecutingBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.work.AsyncWorkTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS;
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS;

/**
 * A {@link TaskExecuter} which executes the actions of a task.
 */
public class ExecuteActionsTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteActionsTaskExecuter.class);

    private final boolean buildCacheEnabled;
    private final boolean scanPluginApplied;
    private final TaskSnapshotter taskSnapshotter;
    private final ExecutionHistoryStore executionHistoryStore;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final TaskActionListener actionListener;
    private final TaskCacheabilityResolver taskCacheabilityResolver;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final WorkExecutor<AfterPreviousExecutionContext, CachingResult> workExecutor;
    private final ListenerManager listenerManager;

    public ExecuteActionsTaskExecuter(
        boolean buildCacheEnabled,
        boolean scanPluginApplied,
        TaskSnapshotter taskSnapshotter,
        ExecutionHistoryStore executionHistoryStore,
        BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker,
        TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        WorkExecutor<AfterPreviousExecutionContext, CachingResult> workExecutor,
        ListenerManager listenerManager
    ) {
        this.buildCacheEnabled = buildCacheEnabled;
        this.scanPluginApplied = scanPluginApplied;
        this.taskSnapshotter = taskSnapshotter;
        this.executionHistoryStore = executionHistoryStore;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.actionListener = actionListener;
        this.taskCacheabilityResolver = taskCacheabilityResolver;
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.workExecutor = workExecutor;
        this.listenerManager = listenerManager;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskExecution work = new TaskExecution(task, context, executionHistoryStore, fingerprinterRegistry, classLoaderHierarchyHasher);
        CachingResult result = workExecutor.execute(new AfterPreviousExecutionContext() {
            @Override
            public UnitOfWork getWork() {
                return work;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getTaskExecutionMode().getRebuildReason();
            }

            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return Optional.ofNullable(context.getAfterPreviousExecution());
            }
        });
        result.getOutcome().ifSuccessfulOrElse(
            new Consumer<ExecutionOutcome>() {
                @Override
                public void accept(ExecutionOutcome outcome) {
                    state.setOutcome(TaskExecutionOutcome.valueOf(outcome));
                }
            },
            new Consumer<Throwable>() {
                @Override
                public void accept(Throwable failure) {
                    state.setOutcome(new TaskExecutionException(task, failure));
                }
            }
        );
        return new TaskExecuterResult() {
            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return result.isReused()
                    ? Optional.of(result.getOriginMetadata())
                    : Optional.empty();
            }

            @Override
            public boolean executedIncrementally() {
                return result.getOutcome()
                    .map(new Function<ExecutionOutcome, Boolean>() {
                        @Override
                        public Boolean apply(ExecutionOutcome executionOutcome) {
                            return executionOutcome == ExecutionOutcome.EXECUTED_INCREMENTALLY;
                        }
                    }).orElseMapFailure(new Function<Throwable, Boolean>() {
                        @Override
                        public Boolean apply(Throwable throwable) {
                            return false;
                        }
                    });
            }

            @Override
            public List<String> getExecutionReasons() {
                return result.getExecutionReasons();
            }

            @Override
            public CachingState getCachingState() {
                return result.getCachingState();
            }
        };
    }

    private class TaskExecution implements UnitOfWork {
        private final TaskInternal task;
        private final TaskExecutionContext context;
        private final ExecutionHistoryStore executionHistoryStore;
        private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;

        public TaskExecution(
            TaskInternal task,
            TaskExecutionContext context,
            ExecutionHistoryStore executionHistoryStore,
            FileCollectionFingerprinterRegistry fingerprinterRegistry,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
            this.task = task;
            this.context = context;
            this.executionHistoryStore = executionHistoryStore;
            this.fingerprinterRegistry = fingerprinterRegistry;
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        }

        @Override
        public String getIdentity() {
            return task.getPath();
        }

        @Override
        public WorkResult execute(@Nullable InputChangesInternal inputChanges) {
            task.getState().setExecuting(true);
            try {
                LOGGER.debug("Executing actions for {}.", task);
                actionListener.beforeActions(task);
                executeActions(task, inputChanges);
                return task.getState().getDidWork() ? WorkResult.DID_WORK : WorkResult.DID_NO_WORK;
            } finally {
                task.getState().setExecuting(false);
                actionListener.afterActions(task);
            }
        }

        @Override
        public ExecutionHistoryStore getExecutionHistoryStore() {
            return executionHistoryStore;
        }

        @Override
        public void visitImplementations(ImplementationVisitor visitor) {
            visitor.visitImplementation(task.getClass());

            List<InputChangesAwareTaskAction> taskActions = task.getTaskActions();
            for (InputChangesAwareTaskAction taskAction : taskActions) {
                visitor.visitAdditionalImplementation(taskAction.getActionImplementation(classLoaderHierarchyHasher));
            }
        }

        @Override
        public void visitInputProperties(InputPropertyVisitor visitor) {
            Map<String, Object> inputPropertyValues = context.getTaskProperties().getInputPropertyValues().get();
            for (Map.Entry<String, Object> entry : inputPropertyValues.entrySet()) {
                String propertyName = entry.getKey();
                Object value = entry.getValue();
                visitor.visitInputProperty(propertyName, value);
            }
        }

        @Override
        public void visitInputFileProperties(InputFilePropertyVisitor visitor) {
            ImmutableSortedSet<InputFilePropertySpec> inputFileProperties = context.getTaskProperties().getInputFileProperties();
            for (InputFilePropertySpec inputFileProperty : inputFileProperties) {
                Object value = inputFileProperty.getValue();
                boolean incremental = inputFileProperty.isIncremental()
                    // SkipWhenEmpty implies incremental.
                    // If this file property is empty, then we clean up the previously generated outputs.
                    // That means that there is a very close relation between the file property and the output.
                    || inputFileProperty.isSkipWhenEmpty();
                String propertyName = inputFileProperty.getPropertyName();
                visitor.visitInputFileProperty(propertyName, value, incremental, () -> {
                    FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(inputFileProperty.getNormalizer());
                    return fingerprinter.fingerprint(inputFileProperty.getPropertyFiles());
                });
            }
        }

        @Override
        public void visitOutputProperties(OutputPropertyVisitor visitor) {
            for (OutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                visitor.visitOutputProperty(property.getPropertyName(), property.getOutputType(), property.getPropertyFiles());
            }
        }

        @Override
        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            for (OutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                if (!(property instanceof CacheableOutputFilePropertySpec)) {
                    throw new IllegalStateException("Non-cacheable property: " + property);
                }
                File cacheRoot = ((CacheableOutputFilePropertySpec) property).getOutputFile();
                if (cacheRoot == null) {
                    continue;
                }

                visitor.visitOutputTree(property.getPropertyName(), property.getOutputType(), cacheRoot);
            }
        }

        @Override
        public void visitLocalState(LocalStateVisitor visitor) {
            for (File localStateFile : context.getTaskProperties().getLocalStateFiles()) {
                visitor.visitLocalStateRoot(localStateFile);
            }
        }

        @Override
        public boolean hasOverlappingOutputs() {
            return context.getOverlappingOutputs().isPresent();
        }

        @Override
        public boolean shouldCleanupOutputsOnNonIncrementalExecution() {
            return getInputChangeTrackingStrategy() == InputChangeTrackingStrategy.INCREMENTAL_PARAMETERS;
        }

        @Override
        public Optional<? extends Iterable<String>> getChangingOutputs() {
            return Optional.empty();
        }

        @Override
        public Optional<CachingDisabledReason> shouldDisableCaching() {
            if (task.isHasCustomActions()) {
                LOGGER.info("Custom actions are attached to {}.", task);
            }

            return taskCacheabilityResolver.shouldDisableCaching(
                context.getTaskProperties().hasDeclaredOutputs(),
                context.getTaskProperties().getOutputFileProperties(),
                task,
                task.getOutputs().getCacheIfSpecs(),
                task.getOutputs().getDoNotCacheIfSpecs(),
                context.getOverlappingOutputs().orElse(null)
            );
        }

        @Override
        public boolean isTaskHistoryMaintained() {
            return context.getTaskExecutionMode().isTaskHistoryMaintained();
        }

        @Override
        public boolean isAllowedToLoadFromCache() {
            return context.getTaskExecutionMode().isAllowedToUseCachedResults();
        }

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.ofNullable(task.getTimeout().getOrNull());
        }

        @SuppressWarnings("deprecation")
        @Override
        public InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
            for (InputChangesAwareTaskAction taskAction : task.getTaskActions()) {
                if (taskAction instanceof IncrementalInputsTaskAction) {
                    return InputChangeTrackingStrategy.INCREMENTAL_PARAMETERS;
                }
                if (taskAction instanceof IncrementalTaskInputsTaskAction) {
                    return InputChangeTrackingStrategy.ALL_PARAMETERS;
                }
            }
            return InputChangeTrackingStrategy.NONE;
        }

        @Override
        public ImmutableSortedMap<String, FileSystemSnapshot> getOutputFileSnapshotsBeforeExecution() {
            return context.getOutputFilesBeforeExecution();
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            AfterPreviousExecutionState afterPreviousExecutionState = context.getAfterPreviousExecution();
            ImmutableSortedMap<String, FileSystemSnapshot> outputsAfterExecution = taskSnapshotter.snapshotTaskFiles(task, context.getTaskProperties().getOutputFileProperties());
            return createFilteredOutputFingerprints(afterPreviousExecutionState, context.getOutputFilesBeforeExecution(), outputsAfterExecution, context.getOverlappingOutputs().isPresent());
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> createFilteredOutputFingerprints(@Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots, ImmutableSortedMap<String, FileSystemSnapshot> afterExecutionOutputSnapshots, boolean hasOverlappingOutputs) {
            ImmutableSortedMap<String, FileCollectionFingerprint> afterLastExecutionFingerprints = afterPreviousExecutionState == null ? ImmutableSortedMap.of() : afterPreviousExecutionState.getOutputFileProperties();

            return ImmutableSortedMap.copyOfSorted(
                Maps.transformEntries(afterExecutionOutputSnapshots, (propertyName, afterExecutionOutputSnapshot) -> {
                        FileCollectionFingerprint afterLastExecutionFingerprint = afterLastExecutionFingerprints.get(propertyName);
                        FileSystemSnapshot beforeExecutionOutputSnapshot = beforeExecutionOutputSnapshots.get(propertyName);
                        return fingerprintOutputSnapshot(afterLastExecutionFingerprint, beforeExecutionOutputSnapshot, afterExecutionOutputSnapshot, hasOverlappingOutputs);
                    }
                )
            );
        }

        private CurrentFileCollectionFingerprint fingerprintOutputSnapshot(@Nullable FileCollectionFingerprint afterLastExecutionFingerprint, FileSystemSnapshot beforeExecutionOutputSnapshot, FileSystemSnapshot afterExecutionOutputSnapshot, boolean hasOverlappingOutputs) {
            List<FileSystemSnapshot> roots = hasOverlappingOutputs
                ? OutputFilterUtil.filterOutputSnapshotAfterExecution(afterLastExecutionFingerprint, beforeExecutionOutputSnapshot, afterExecutionOutputSnapshot)
                : ImmutableList.of(afterExecutionOutputSnapshot);
            return DefaultCurrentFileCollectionFingerprint.from(roots, AbsolutePathFingerprintingStrategy.IGNORE_MISSING);
        }

        @Override
        public long markExecutionTime() {
            return context.markExecutionTime();
        }

        @Override
        public void markSnapshottingInputsFinished(CachingState cachingState) {
            // TODO:lptr this should be added only if the scan plugin is applied, but SnapshotTaskInputsOperationIntegrationTest
            //   expects it to be added also when the build cache is enabled (but not the scan plugin)
            if (buildCacheEnabled || scanPluginApplied) {
                context.removeSnapshotTaskInputsBuildOperation()
                    .ifPresent(new Consumer<ExecutingBuildOperation>() {
                        @Override
                        public void accept(ExecutingBuildOperation operation) {
                            operation.setResult(new SnapshotTaskInputsBuildOperationResult(cachingState));
                        }
                    });
            }
        }

        @Override
        public String getDisplayName() {
            return task.toString();
        }
    }

    private void executeActions(TaskInternal task, @Nullable InputChangesInternal inputChanges) {
        boolean hasTaskListener = listenerManager.hasListeners(TaskActionListener.class) || listenerManager.hasListeners(TaskExecutionListener.class);
        Iterator<InputChangesAwareTaskAction> actions = new ArrayList<>(task.getTaskActions()).iterator();
        while (actions.hasNext()) {
            InputChangesAwareTaskAction action = actions.next();
            task.getState().setDidWork(true);
            task.getStandardOutputCapture().start();
            boolean hasMoreWork = hasTaskListener || actions.hasNext();
            try {
                executeAction(action.getDisplayName(), task, action, inputChanges, hasMoreWork);
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

    private void executeAction(String actionDisplayName, TaskInternal task, InputChangesAwareTaskAction action, @Nullable InputChangesInternal inputChanges, boolean hasMoreWork) {
        if (inputChanges != null) {
            action.setInputChanges(inputChanges);
        }
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                        .displayName(actionDisplayName + " for " + task.getIdentityPath().getPath())
                        .name(actionDisplayName)
                        .details(ExecuteTaskActionBuildOperationType.DETAILS_INSTANCE);
            }

            @Override
            public void run(BuildOperationContext context) {
                try {
                    BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
                    Throwable actionFailure = null;
                    try {
                        action.execute(task);
                    } catch (Throwable t) {
                        actionFailure = t;
                    } finally {
                        action.clearInputChanges();
                    }

                    try {
                        asyncWorkTracker.waitForCompletion(currentOperation, hasMoreWork ? RELEASE_AND_REACQUIRE_PROJECT_LOCKS : RELEASE_PROJECT_LOCKS);
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
                        context.failed(actionFailure);
                        throw UncheckedException.throwAsUncheckedException(actionFailure);
                    }
                } finally {
                    context.setResult(ExecuteTaskActionBuildOperationType.RESULT_INSTANCE);
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
