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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
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
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.InputPropertySpec;
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
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionEngine.Result;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.work.AsyncWorkTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.NON_IDENTITY;
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

    private final ExecutionHistoryStore executionHistoryStore;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final TaskActionListener actionListener;
    private final TaskCacheabilityResolver taskCacheabilityResolver;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ExecutionEngine executionEngine;
    private final ListenerManager listenerManager;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final EmptySourceTaskSkipper emptySourceTaskSkipper;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileOperations fileOperations;

    public ExecuteActionsTaskExecuter(
        BuildCacheState buildCacheState,
        ScanPluginState scanPluginState,

        ExecutionHistoryStore executionHistoryStore,
        BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker,
        TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionEngine executionEngine,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        EmptySourceTaskSkipper emptySourceTaskSkipper,
        FileCollectionFactory fileCollectionFactory,
        FileOperations fileOperations
    ) {
        this.buildCacheState = buildCacheState;
        this.scanPluginState = scanPluginState;

        this.executionHistoryStore = executionHistoryStore;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.actionListener = actionListener;
        this.taskCacheabilityResolver = taskCacheabilityResolver;
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.executionEngine = executionEngine;
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
        ExecutionEngine.Request request = executionEngine.createRequest(work);
        context.getTaskExecutionMode().getRebuildReason().ifPresent(request::forceRebuild);
        request.withValidationContext(context.getValidationContext());
        Result result = request.execute();
        result.getExecutionResult().ifSuccessfulOrElse(
            executionResult -> state.setOutcome(TaskExecutionOutcome.valueOf(executionResult.getOutcome())),
            failure -> state.setOutcome(new TaskExecutionException(task, failure))
        );
        return new TaskExecuterResult() {
            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return result.getReusedOutputOriginMetadata();
            }

            @Override
            public boolean executedIncrementally() {
                return result.getExecutionResult()
                    .map(executionResult -> executionResult.getOutcome() == ExecutionOutcome.EXECUTED_INCREMENTALLY)
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
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher
        ) {
            this.task = task;
            this.context = context;
            this.executionHistoryStore = executionHistoryStore;
            this.fingerprinterRegistry = fingerprinterRegistry;
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        }

        @Override
        public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
            return task::getPath;
        }

        @Override
        public WorkOutput execute(ExecutionRequest executionRequest) {
            FileCollection previousFiles = executionRequest.getPreviouslyProducedOutputs()
                .<FileCollection>map(previousOutputs -> new PreviousOutputFileCollection(task, previousOutputs))
                .orElseGet(fileCollectionFactory::empty);
            TaskOutputsInternal outputs = task.getOutputs();
            outputs.setPreviousOutputFiles(previousFiles);
            try {
                WorkResult didWork = executeWithPreviousOutputFiles(executionRequest.getInputChanges().orElse(null));
                return new WorkOutput() {
                    @Override
                    public WorkResult getDidWork() {
                        return didWork;
                    }

                    @Override
                    public Object getOutput() {
                        throw new UnsupportedOperationException();
                    }
                };
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
        public void visitImplementations(ImplementationVisitor visitor) {
            visitor.visitImplementation(task.getClass());

            List<InputChangesAwareTaskAction> taskActions = task.getTaskActions();
            for (InputChangesAwareTaskAction taskAction : taskActions) {
                visitor.visitImplementation(taskAction.getActionImplementation(classLoaderHierarchyHasher));
            }
        }

        @Override
        public void visitInputs(InputVisitor visitor) {
            ImmutableSortedSet<InputPropertySpec> inputProperties = context.getTaskProperties().getInputProperties();
            ImmutableSortedSet<InputFilePropertySpec> inputFileProperties = context.getTaskProperties().getInputFileProperties();
            for (InputPropertySpec inputProperty : inputProperties) {
                visitor.visitInputProperty(inputProperty.getPropertyName(), NON_IDENTITY, () -> InputParameterUtils.prepareInputParameterValue(inputProperty, task));
            }
            for (InputFilePropertySpec inputFileProperty : inputFileProperties) {
                Object value = inputFileProperty.getValue();
                // SkipWhenEmpty implies incremental.
                // If this file property is empty, then we clean up the previously generated outputs.
                // That means that there is a very close relation between the file property and the output.
                InputPropertyType type = inputFileProperty.isSkipWhenEmpty()
                    ? InputPropertyType.PRIMARY
                    : inputFileProperty.isIncremental()
                        ? InputPropertyType.INCREMENTAL
                        : InputPropertyType.NON_INCREMENTAL;
                String propertyName = inputFileProperty.getPropertyName();
                visitor.visitInputFileProperty(propertyName, type, NON_IDENTITY, value, () -> {
                    FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(inputFileProperty);
                    return fingerprinter.fingerprint(inputFileProperty.getPropertyFiles());
                });
            }
        }

        @Override
        public void visitOutputs(File workspace, OutputVisitor visitor) {
            for (OutputFilePropertySpec property : context.getTaskProperties().getOutputFileProperties()) {
                File outputFile = property.getOutputFile();
                if (outputFile != null) {
                    visitor.visitOutputProperty(property.getPropertyName(), property.getOutputType(), outputFile, property.getPropertyFiles());
                }
            }
            for (File localStateRoot : context.getTaskProperties().getLocalStateFiles()) {
                visitor.visitLocalState(localStateRoot);
            }
            for (File destroyableRoot : context.getTaskProperties().getDestroyableFiles()) {
                visitor.visitDestroyable(destroyableRoot);
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
            TypeValidationContext typeValidationContext = validationContext.forType(taskType, cacheable);
            context.getTaskProperties().validateType(typeValidationContext);
            context.getTaskProperties().validate(new DefaultTaskValidationContext(
                fileOperations,
                reservedFileSystemLocationRegistry,
                typeValidationContext
            ));
            context.getValidationAction().accept(typeValidationContext);
        }

        @Override
        public Optional<ExecutionOutcome> skipIfInputsEmpty(ImmutableSortedMap<String, FileSystemSnapshot> outputFilesAfterPreviousExecution) {
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

        @Override
        public String toString() {
            return getDisplayName();
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
        private final ImmutableSortedMap<String, FileSystemSnapshot> previousOutputs;

        public PreviousOutputFileCollection(TaskInternal task, ImmutableSortedMap<String, FileSystemSnapshot> previousOutputs) {
            this.task = task;
            this.previousOutputs = previousOutputs;
        }

        @Override
        public FileCollectionInternal createDelegate() {
            List<File> outputs = previousOutputs.values().stream()
                .map(SnapshotUtil::index)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .map(File::new)
                .collect(Collectors.toList());
            return fileCollectionFactory.fixed(outputs);
        }

        @Override
        public String getDisplayName() {
            return "previous output files of " + task.toString();
        }
    }
}
