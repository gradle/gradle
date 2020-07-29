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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.project.taskfactory.IncrementalInputsTaskAction;
import org.gradle.api.internal.project.taskfactory.IncrementalTaskInputsTaskAction;
import org.gradle.api.internal.tasks.DefaultTaskValidationContext;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.CacheableOutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.impl.OutputFilterUtil;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.work.AsyncWorkTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS;
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS;

/**
 * A {@link TaskExecuter} which executes the actions of a task.
 */
public class ExecuteActionsTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteActionsTaskExecuter.class);

    public enum BuildCacheState {
        ENABLED, DISABLED
    }

    public enum ScanPluginState {
        APPLIED, NOT_APPLIED
    }

    private final BuildCacheState buildCacheState;
    private final ScanPluginState scanPluginState;

    private final TaskSnapshotter taskSnapshotter;
    private final ExecutionHistoryStore executionHistoryStore;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final TaskActionListener actionListener;
    private final TaskCacheabilityResolver taskCacheabilityResolver;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor;
    private final ListenerManager listenerManager;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final EmptySourceTaskSkipper emptySourceTaskSkipper;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileOperations fileOperations;

    public ExecuteActionsTaskExecuter(
        BuildCacheState buildCacheState,
        ScanPluginState scanPluginState,

        TaskSnapshotter taskSnapshotter,
        ExecutionHistoryStore executionHistoryStore,
        BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker,
        TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        EmptySourceTaskSkipper emptySourceTaskSkipper,
        FileCollectionFactory fileCollectionFactory,
        FileOperations fileOperations
    ) {
        this.buildCacheState = buildCacheState;
        this.scanPluginState = scanPluginState;

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
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.emptySourceTaskSkipper = emptySourceTaskSkipper;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileOperations = fileOperations;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskExecution work = new TaskExecution(task, context, executionHistoryStore, fingerprinterRegistry, classLoaderHierarchyHasher);
        try {
            return executeIfValid(task, state, context, work);
        } catch (WorkValidationException ex) {
            state.setOutcome(ex);
            return TaskExecuterResult.WITHOUT_OUTPUTS;
        }
    }

    private TaskExecuterResult executeIfValid(TaskInternal task, TaskStateInternal state, TaskExecutionContext context, TaskExecution work) {
        CachingResult result = workExecutor.execute(new ExecutionRequestContext() {
            @Override
            public UnitOfWork getWork() {
                return work;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getTaskExecutionMode().getRebuildReason();
            }
        });
        result.getOutcome().ifSuccessfulOrElse(
            outcome -> state.setOutcome(TaskExecutionOutcome.valueOf(outcome)),
            failure -> state.setOutcome(new TaskExecutionException(task, failure))
        );
        return new TaskExecuterResult() {
            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return result.getReusedOutputOriginMetadata();
            }

            @Override
            public boolean executedIncrementally() {
                return result.getOutcome()
                    .map(executionOutcome -> executionOutcome == ExecutionOutcome.EXECUTED_INCREMENTALLY)
                    .getOrMapFailure(throwable -> false);
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
        public WorkResult execute(@Nullable InputChangesInternal inputChanges, InputChangesContext context) {
            FileCollection previousFiles = context.getAfterPreviousExecutionState()
                .map(afterPreviousExecutionState -> (FileCollection) new PreviousOutputFileCollection(task, afterPreviousExecutionState))
                .orElseGet(fileCollectionFactory::empty);
            TaskOutputsInternal outputs = task.getOutputs();
            outputs.setPreviousOutputFiles(previousFiles);
            try {
                return executeWithPreviousOutputFiles(inputChanges);
            } finally {
                outputs.setPreviousOutputFiles(null);
            }
        }

        private WorkResult executeWithPreviousOutputFiles(@Nullable InputChangesInternal inputChanges) {
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
        public Optional<ExecutionHistoryStore> getExecutionHistoryStore() {
            return context.getTaskExecutionMode().isTaskHistoryMaintained()
                ? Optional.of(executionHistoryStore)
                : Optional.empty();
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
                File outputFile = property.getOutputFile();
                if (outputFile != null) {
                    visitor.visitOutputProperty(property.getPropertyName(), property.getOutputType(), outputFile);
                }
            }
        }

        @Override
        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            for (OutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                if (!(property instanceof CacheableOutputFilePropertySpec)) {
                    throw new IllegalStateException("Non-cacheable property: " + property);
                }
                File cacheRoot = property.getOutputFile();
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
        public OverlappingOutputHandling getOverlappingOutputHandling() {
            return OverlappingOutputHandling.DETECT_OVERLAPS;
        }

        @Override
        public boolean shouldCleanupOutputsOnNonIncrementalExecution() {
            return getInputChangeTrackingStrategy() == InputChangeTrackingStrategy.INCREMENTAL_PARAMETERS;
        }

        @Override
        public Iterable<String> getChangingOutputs() {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            visitOutputProperties((propertyName, type, root) -> builder.add(root.getAbsolutePath()));
            context.getTaskProperties().getDestroyableFiles().forEach(file -> builder.add(file.getAbsolutePath()));
            context.getTaskProperties().getLocalStateFiles().forEach(file -> builder.add(file.getAbsolutePath()));
            return builder.build();
        }

        @Override
        public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
            if (task.isHasCustomActions()) {
                LOGGER.info("Custom actions are attached to {}.", task);
            }

            return taskCacheabilityResolver.shouldDisableCaching(
                context.getTaskProperties().hasDeclaredOutputs(),
                context.getTaskProperties().getOutputFileProperties(),
                task,
                task.getOutputs().getCacheIfSpecs(),
                task.getOutputs().getDoNotCacheIfSpecs(),
                detectedOverlappingOutputs
            );
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
        public ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsBeforeExecution() {
            return snapshotOutputs();
        }

        @Override
        public ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsAfterExecution() {
            return snapshotOutputs();
        }

        private ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputs() {
            ImmutableSortedSet<OutputFilePropertySpec> outputFilePropertySpecs = context.getTaskProperties().getOutputFileProperties();
            return taskSnapshotter.snapshotTaskFiles(task, outputFilePropertySpecs);
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintAndFilterOutputSnapshots(
            ImmutableSortedMap<String, FileCollectionFingerprint> afterPreviousExecutionOutputFingerprints,
            ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots,
            ImmutableSortedMap<String, FileSystemSnapshot> afterExecutionOutputSnapshots,
            boolean hasDetectedOverlappingOutputs
        ) {
            return ImmutableSortedMap.copyOfSorted(
                Maps.transformEntries(afterExecutionOutputSnapshots, (propertyName, afterExecutionOutputSnapshot) -> {
                        FileCollectionFingerprint afterLastExecutionFingerprint = afterPreviousExecutionOutputFingerprints.get(propertyName);
                        FileSystemSnapshot beforeExecutionOutputSnapshot = beforeExecutionOutputSnapshots.get(propertyName);
                        // This can never be null as it comes from an ImmutableMap's value
                        assert afterExecutionOutputSnapshot != null;
                        return fingerprintOutputSnapshot(afterLastExecutionFingerprint, beforeExecutionOutputSnapshot, afterExecutionOutputSnapshot, hasDetectedOverlappingOutputs);
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
        public void markLegacySnapshottingInputsStarted() {
            // Note: this operation should be added only if the scan plugin is applied, but SnapshotTaskInputsOperationIntegrationTest
            //   expects it to be added also when the build cache is enabled (but not the scan plugin)
            if (buildCacheState == BuildCacheState.ENABLED || scanPluginState == ScanPluginState.APPLIED) {
                BuildOperationContext operationContext = buildOperationExecutor.start(BuildOperationDescriptor
                    .displayName("Snapshot task inputs for " + task.getIdentityPath())
                    .name("Snapshot task inputs")
                    .details(SnapshotTaskInputsBuildOperationType.Details.INSTANCE));
                context.setSnapshotTaskInputsBuildOperationContext(operationContext);
            }
        }

        @Override
        public void markLegacySnapshottingInputsFinished(CachingState cachingState) {
            context.removeSnapshotTaskInputsBuildOperationContext()
                .ifPresent(operation -> operation.setResult(new SnapshotTaskInputsBuildOperationResult(cachingState)));
        }

        @Override
        public void ensureLegacySnapshottingInputsClosed() {
            // If the operation hasn't finished normally (because of a shortcut or an error), we close it without a cache key
            context.removeSnapshotTaskInputsBuildOperationContext()
                .ifPresent(operation -> operation.setResult(new SnapshotTaskInputsBuildOperationResult(CachingState.NOT_DETERMINED)));
        }

        @Override
        public void validate(WorkValidationContext validationContext) {
            Class<?> taskType = GeneratedSubclasses.unpackType(task);
            // TODO This should probably use the task class info store
            boolean cacheable = taskType.isAnnotationPresent(CacheableTask.class);
            context.getTaskProperties().validate(new DefaultTaskValidationContext(
                fileOperations,
                reservedFileSystemLocationRegistry,
                validationContext.createContextFor(taskType, cacheable)
            ));
        }

        @Override
        public Optional<ExecutionOutcome> skipIfInputsEmpty(ImmutableSortedMap<String, FileCollectionFingerprint> outputFilesAfterPreviousExecution) {
            TaskProperties properties = context.getTaskProperties();
            FileCollection inputFiles = properties.getInputFiles();
            FileCollection sourceFiles = properties.getSourceFiles();
            boolean hasSourceFiles = properties.hasSourceFiles();
            return emptySourceTaskSkipper.skipIfEmptySources(task, hasSourceFiles, inputFiles, sourceFiles, outputFilesAfterPreviousExecution);
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

    private class PreviousOutputFileCollection extends LazilyInitializedFileCollection {
        private final TaskInternal task;
        private final AfterPreviousExecutionState previousExecution;

        public PreviousOutputFileCollection(TaskInternal task, AfterPreviousExecutionState previousExecution) {
            this.task = task;
            this.previousExecution = previousExecution;
        }

        @Override
        public FileCollectionInternal createDelegate() {
            ImmutableCollection<FileCollectionFingerprint> outputFingerprints = previousExecution.getOutputFileProperties().values();
            Set<File> outputs = new HashSet<>();
            for (FileCollectionFingerprint fileCollectionFingerprint : outputFingerprints) {
                for (String absolutePath : fileCollectionFingerprint.getFingerprints().keySet()) {
                    outputs.add(new File(absolutePath));
                }
            }
            return fileCollectionFactory.fixed(outputs);
        }

        @Override
        public String getDisplayName() {
            return "previous output files of " + task.toString();
        }
    }
}
