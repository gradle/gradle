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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsEnterpriseInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.project.taskfactory.IncrementalTaskAction;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.properties.DefaultPropertyValidationContext;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.InputPropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.Sync;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.work.AsyncWorkTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS;
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS;

@SuppressWarnings("deprecation")
public class TaskExecution implements MutableUnitOfWork {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecution.class);
    private static final SnapshotTaskInputsBuildOperationType.Details SNAPSHOT_TASK_INPUTS_DETAILS = new SnapshotTaskInputsBuildOperationType.Details() {
    };

    private final TaskInternal task;
    private final TaskExecutionContext context;
    private final boolean emitLegacySnapshottingOperations;

    private final org.gradle.api.execution.TaskActionListener actionListener;
    private final AsyncWorkTracker asyncWorkTracker;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ExecutionHistoryStore executionHistoryStore;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final PathToFileResolver fileResolver;
    private final InputFingerprinter inputFingerprinter;
    private final ListenerManager listenerManager;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final TaskCacheabilityResolver taskCacheabilityResolver;

    public TaskExecution(
        TaskInternal task,
        TaskExecutionContext context,
        boolean emitLegacySnapshottingOperations,

        org.gradle.api.execution.TaskActionListener actionListener,
        AsyncWorkTracker asyncWorkTracker,
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionHistoryStore executionHistoryStore,
        FileCollectionFactory fileCollectionFactory,
        PathToFileResolver fileResolver,
        InputFingerprinter inputFingerprinter,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        TaskCacheabilityResolver taskCacheabilityResolver,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.task = task;
        this.context = context;
        this.emitLegacySnapshottingOperations = emitLegacySnapshottingOperations;

        this.actionListener = actionListener;
        this.asyncWorkTracker = asyncWorkTracker;
        this.buildOperationExecutor = buildOperationExecutor;
        this.executionHistoryStore = executionHistoryStore;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.fileCollectionFactory = fileCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.fileResolver = fileResolver;
        this.inputFingerprinter = inputFingerprinter;
        this.listenerManager = listenerManager;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.taskCacheabilityResolver = taskCacheabilityResolver;
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        return task::getPath;
    }

    @Override
    public WorkOutput execute(ExecutionRequest executionRequest) {
        FileCollection previousFiles = executionRequest.getPreviouslyProducedOutputs()
            .<FileCollection>map(previousOutputs -> new PreviousOutputFileCollection(task, taskDependencyFactory, fileCollectionFactory, previousOutputs))
            .orElseGet(FileCollectionFactory::empty);
        TaskOutputsEnterpriseInternal outputs = (TaskOutputsEnterpriseInternal) task.getOutputs();
        outputs.setPreviousOutputFiles(previousFiles);
        try {
            WorkResult didWork = executeWithPreviousOutputFiles(executionRequest.getInputChanges().orElse(null));
            boolean storeInCache = outputs.getStoreInCache();
            return new WorkOutput() {
                @Override
                public WorkResult getDidWork() {
                    return didWork;
                }

                @Override
                public Object getOutput() {
                    return null;
                }

                @Override
                public boolean canStoreInCache() {
                    return storeInCache;
                }
            };
        } finally {
            outputs.setPreviousOutputFiles(null);
        }
    }

    @Override
    public Object loadAlreadyProducedOutput(File workspace) {
        return null;
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

    private void executeActions(TaskInternal task, @Nullable InputChangesInternal inputChanges) {
        boolean hasTaskListener = listenerManager.hasListeners(org.gradle.api.execution.TaskActionListener.class) || listenerManager.hasListeners(org.gradle.api.execution.TaskExecutionListener.class);
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

    @Override
    public WorkspaceProvider getWorkspaceProvider() {
        return new WorkspaceProvider() {
            @Override
            public <T> T withWorkspace(String path, WorkspaceAction<T> action) {
                return action.executeInWorkspace(null, context.getTaskExecutionMode().isTaskHistoryMaintained()
                    ? executionHistoryStore
                    : null);
            }
        };
    }

    @Override
    public InputFingerprinter getInputFingerprinter() {
        return inputFingerprinter;
    }

    @Override
    public void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(task.getClass());

        List<InputChangesAwareTaskAction> taskActions = task.getTaskActions();
        for (InputChangesAwareTaskAction taskAction : taskActions) {
            visitor.visitImplementation(taskAction.getActionImplementation(classLoaderHierarchyHasher));
        }
    }

    @Override
    public void visitRegularInputs(InputVisitor visitor) {
        TaskProperties taskProperties = context.getTaskProperties();
        for (InputPropertySpec inputProperty : taskProperties.getInputProperties()) {
            visitor.visitInputProperty(
                inputProperty.getPropertyName(),
                () -> InputParameterUtils.prepareInputParameterValue(inputProperty, task));
        }
        for (InputFilePropertySpec inputFileProperty : taskProperties.getInputFileProperties()) {
            // SkipWhenEmpty implies incremental.
            // If this file property is empty, then we clean up the previously generated outputs.
            // That means that there is a very close relation between the file property and the output.
            try {
                visitor.visitInputFileProperty(
                    inputFileProperty.getPropertyName(),
                    inputFileProperty.getBehavior(),
                    new InputFileValueSupplier(
                        inputFileProperty.getValue(),
                        inputFileProperty.getNormalizer(),
                        inputFileProperty.getDirectorySensitivity(),
                        inputFileProperty.getLineEndingNormalization(),
                        inputFileProperty::getPropertyFiles));
            } catch (InputFingerprinter.InputFileFingerprintingException e) {
                throw decorateSnapshottingException("input", inputFileProperty.getPropertyName(), e.getCause());
            }
        }
    }

    @Override
    public void visitOutputs(File workspace, OutputVisitor visitor) {
        TaskProperties taskProperties = context.getTaskProperties();
        for (OutputFilePropertySpec property : taskProperties.getOutputFileProperties()) {
            try {
                visitor.visitOutputProperty(
                    property.getPropertyName(),
                    property.getOutputType(),
                    OutputFileValueSupplier.fromSupplier(property::getOutputFile, property.getPropertyFiles())
                );
            } catch (OutputSnapshotter.OutputFileSnapshottingException e) {
                throw decorateSnapshottingException("output", property.getPropertyName(), e.getCause());
            }
        }
        for (File localStateRoot : taskProperties.getLocalStateFiles()) {
            visitor.visitLocalState(localStateRoot);
        }
        for (File destroyableRoot : taskProperties.getDestroyableFiles()) {
            visitor.visitDestroyable(destroyableRoot);
        }
    }

    private RuntimeException decorateSnapshottingException(String propertyType, String propertyName, Throwable cause) {
        if (!(cause instanceof UncheckedIOException || cause instanceof org.gradle.api.UncheckedIOException)) {
            return UncheckedException.throwAsUncheckedException(cause);
        }
        boolean isDestinationDir = propertyName.equals("destinationDir");
        DocumentedFailure.Builder builder = DocumentedFailure.builder();
        if (isDestinationDir && task instanceof Copy) {
            builder.withSummary("Cannot access a file in the destination directory.")
                .withContext("Copying to a directory which contains unreadable content is not supported.")
                .withAdvice("Declare the task as untracked by using Task.doNotTrackState().");
        } else if (isDestinationDir && task instanceof Sync) {
            builder.withSummary("Cannot access a file in the destination directory.")
                .withContext("Syncing to a directory which contains unreadable content is not supported.")
                .withAdvice("Use a Copy task with Task.doNotTrackState() instead.");
        } else {
            builder.withSummary(String.format("Cannot access %s property '%s' of %s.",
                    propertyType, propertyName, getDisplayName()))
                .withContext("Accessing unreadable inputs or outputs is not supported.")
                .withAdvice("Declare the task as untracked by using Task.doNotTrackState().");
        }
        return builder.withUserManual("incremental_build", "disable-state-tracking")
            .build(cause);
    }

    @Override
    public OverlappingOutputHandling getOverlappingOutputHandling() {
        return OverlappingOutputHandling.DETECT_OVERLAPS;
    }

    @Override
    public boolean shouldCleanupStaleOutputs() {
        return context.getTaskExecutionMode().isTaskHistoryMaintained();
    }

    @Override
    public boolean shouldCleanupOutputsOnNonIncrementalExecution() {
        return getExecutionBehavior() == ExecutionBehavior.INCREMENTAL;
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        if (task.isHasCustomActions()) {
            LOGGER.info("Custom actions are attached to {}.", task);
        }

        return taskCacheabilityResolver.shouldDisableCaching(
            task,
            context.getTaskProperties(),
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

    @Override
    public ExecutionBehavior getExecutionBehavior() {
        for (InputChangesAwareTaskAction taskAction : task.getTaskActions()) {
            if (taskAction instanceof IncrementalTaskAction) {
                return ExecutionBehavior.INCREMENTAL;
            }
        }
        return ExecutionBehavior.NON_INCREMENTAL;
    }

    @Override
    public void markLegacySnapshottingInputsStarted() {
        // Note: this operation should be added only if the scan plugin is applied, but SnapshotTaskInputsOperationIntegrationTest
        //   expects it to be added also when the build cache is enabled (but not the scan plugin)
        if (emitLegacySnapshottingOperations) {
            BuildOperationContext operationContext = buildOperationExecutor.start(BuildOperationDescriptor
                .displayName("Snapshot task inputs for " + task.getIdentityPath())
                .name("Snapshot task inputs")
                .details(SNAPSHOT_TASK_INPUTS_DETAILS));
            context.setSnapshotTaskInputsBuildOperationContext(operationContext);
        }
    }

    @Override
    public void markLegacySnapshottingInputsFinished(CachingState cachingState) {
        context.removeSnapshotTaskInputsBuildOperationContext()
            .ifPresent(operation -> operation.setResult(new SnapshotTaskInputsBuildOperationResult(cachingState, context.getTaskProperties().getInputFileProperties())));
    }

    @Override
    public void ensureLegacySnapshottingInputsClosed() {
        // If the operation hasn't finished normally (because of a shortcut or an error), we close it without a cache key
        context.removeSnapshotTaskInputsBuildOperationContext()
            .ifPresent(operation -> operation.setResult(new SnapshotTaskInputsBuildOperationResult(CachingState.NOT_DETERMINED, Collections.emptySet())));
    }

    @Override
    public void validate(WorkValidationContext validationContext) {
        Class<?> taskType = GeneratedSubclasses.unpackType(task);
        // TODO This should probably use the task class info store
        boolean cacheable = taskType.isAnnotationPresent(CacheableTask.class);
        TypeValidationContext typeValidationContext = validationContext.forType(taskType, cacheable);
        context.getTaskProperties().validateType(typeValidationContext);
        context.getTaskProperties().validate(new DefaultPropertyValidationContext(
            fileResolver,
            reservedFileSystemLocationRegistry,
            typeValidationContext
        ));
        context.getValidationAction().validate(typeValidationContext);
    }

    @Override
    public String getDisplayName() {
        return task.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static class PreviousOutputFileCollection extends LazilyInitializedFileCollection {
        private final TaskInternal task;
        private final FileCollectionFactory fileCollectionFactory;
        private final ImmutableSortedMap<String, FileSystemSnapshot> previousOutputs;

        public PreviousOutputFileCollection(TaskInternal task, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory, ImmutableSortedMap<String, FileSystemSnapshot> previousOutputs) {
            super(taskDependencyFactory);
            this.task = task;
            this.fileCollectionFactory = fileCollectionFactory;
            this.previousOutputs = previousOutputs;
        }

        @Override
        public FileCollectionInternal createDelegate() {
            List<File> outputs = previousOutputs.values().stream()
                .map(SnapshotUtil::indexByAbsolutePath)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .map(File::new)
                .collect(Collectors.toList());
            return fileCollectionFactory.fixed(outputs);
        }

        @Override
        public String getDisplayName() {
            return "previous output files of " + task;
        }
    }

    @Contextual
    private static class MultipleTaskActionFailures extends DefaultMultiCauseException {
        public MultipleTaskActionFailures(String message, Iterable<? extends Throwable> causes) {
            super(message, causes);
        }
    }
}
