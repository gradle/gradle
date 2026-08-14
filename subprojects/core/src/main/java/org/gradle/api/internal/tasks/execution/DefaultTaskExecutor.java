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
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskExecutor;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.logging.Logger;
import org.gradle.api.problems.internal.ProblemTaskIdentityTracker;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.execution.plan.MissingTaskDependencyDetector;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.Execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.AsyncWorkTracker;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import static org.gradle.internal.execution.Execution.ExecutionOutcome.EXECUTED_INCREMENTALLY;

/**
 * Default implementation of {@link TaskExecutor}.
 */
public class DefaultTaskExecutor implements TaskExecutor {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DefaultTaskExecutor.class);

    @SuppressWarnings("deprecation")
    private final org.gradle.api.execution.TaskExecutionListener taskExecutionListener;
    private final TaskListenerInternal taskListener;
    private final TaskExecutionModeResolver executionModeResolver;
    private final TaskExecutionGraph taskExecutionGraph;
    private final ExecutionHistoryStore executionHistoryStore;
    private final BuildOperationRunner buildOperationRunner;
    private final AsyncWorkTracker asyncWorkTracker;
    @SuppressWarnings("deprecation")
    private final org.gradle.api.execution.TaskActionListener actionListener;
    private final TaskCacheabilityResolver taskCacheabilityResolver;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ExecutionEngine executionEngine;
    private final InputFingerprinter inputFingerprinter;
    private final ListenerManager listenerManager;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final PathToFileResolver fileResolver;
    private final MissingTaskDependencyDetector missingTaskDependencyDetector;

    public DefaultTaskExecutor(
        BuildOperationRunner buildOperationRunner,
        @SuppressWarnings("deprecation")
        org.gradle.api.execution.TaskExecutionListener taskExecutionListener,
        TaskListenerInternal taskListener,
        TaskExecutionModeResolver executionModeResolver,
        TaskExecutionGraph taskExecutionGraph,
        ExecutionHistoryStore executionHistoryStore,
        AsyncWorkTracker asyncWorkTracker,
        @SuppressWarnings("deprecation")
        org.gradle.api.execution.TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionEngine executionEngine,
        InputFingerprinter inputFingerprinter,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        FileCollectionFactory fileCollectionFactory,
        TaskDependencyFactory taskDependencyFactory,
        PathToFileResolver fileResolver,
        MissingTaskDependencyDetector missingTaskDependencyDetector
    ) {
        this.taskExecutionListener = taskExecutionListener;
        this.taskListener = taskListener;
        this.executionModeResolver = executionModeResolver;
        this.taskExecutionGraph = taskExecutionGraph;
        this.executionHistoryStore = executionHistoryStore;
        this.buildOperationRunner = buildOperationRunner;
        this.asyncWorkTracker = asyncWorkTracker;
        this.actionListener = actionListener;
        this.taskCacheabilityResolver = taskCacheabilityResolver;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.executionEngine = executionEngine;
        this.inputFingerprinter = inputFingerprinter;
        this.listenerManager = listenerManager;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.fileResolver = fileResolver;
        this.missingTaskDependencyDetector = missingTaskDependencyDetector;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext operationContext) {
                operationContext.setResult(executeWithLifecycle(task, state, context));
                operationContext.setStatus(state.getFailure() != null ? "FAILED" : state.getSkipMessage());
                operationContext.failed(state.getFailure());
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                ExecuteTaskBuildOperationDetails taskOperation = new ExecuteTaskBuildOperationDetails(context.getLocalTaskNode());
                return BuildOperationDescriptor.displayName("Task " + task.getIdentityPath())
                    .name(task.getIdentityPath().asString())
                    .progressDisplayName(task.getIdentityPath().asString())
                    .metadata(BuildOperationCategory.TASK)
                    .details(taskOperation);
            }
        });
    }

    /**
     * Executes before-task and after-task listeners, returning the build operation result of
     * executing the task. A null result is returned when the task did not return due to a failure
     * executing a before-task listener.
     */
    private @Nullable ExecuteTaskBuildOperationResult executeWithLifecycle(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        ContextAwareTaskLogger contextAwareTaskLogger = null;
        try {
            Logger logger = task.getLogger();
            taskListener.beforeExecute(task.getTaskIdentity());
            taskExecutionListener.beforeExecute(task);
            if (logger instanceof ContextAwareTaskLogger) {
                contextAwareTaskLogger = (ContextAwareTaskLogger) logger;
                BuildOperationRef currentOperation = buildOperationRunner.getCurrentOperation();
                contextAwareTaskLogger.setFallbackBuildOperationId(currentOperation.getId());
            }
        } catch (Throwable t) {
            state.setOutcome(new TaskExecutionException(task, t));
            return null;
        }

        ExecutionEngine.Result result;
        try {
            result = executeCatchingFailures(task, state, context);
        } finally {
            if (contextAwareTaskLogger != null) {
                contextAwareTaskLogger.setFallbackBuildOperationId(null);
            }
        }
        ExecuteTaskBuildOperationResult operationResult = toOperationResult(state, result);

        try {
            taskExecutionListener.afterExecute(task, state);
            taskListener.afterExecute(task.getTaskIdentity(), state);
        } catch (Throwable t) {
            state.addFailure(new TaskExecutionException(task, t));
        }
        return operationResult;
    }

    private static ExecuteTaskBuildOperationResult toOperationResult(TaskStateInternal taskState, ExecutionEngine.@Nullable Result result) {
        if (result == null) {
            return new ExecuteTaskBuildOperationResult(
                taskState,
                CachingState.NOT_DETERMINED,
                null,
                false,
                ImmutableList.of()
            );
        }

        boolean incremental = result.getExecution()
            .map(executionResult -> executionResult.getOutcome() == EXECUTED_INCREMENTALLY)
            .getOrMapFailure(throwable -> false);
        return new ExecuteTaskBuildOperationResult(
            taskState,
            result.getCachingState(),
            result.getReusedOutputOriginMetadata().orElse(null),
            incremental,
            result.getExecutionReasons()
        );
    }

    private ExecutionEngine.@Nullable Result executeCatchingFailures(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        try {
            return executeSkippingOnlyIf(task, state, context);
        } catch (RuntimeException e) {
            state.setOutcome(new TaskExecutionException(task, e));
            return null;
        }
    }

    /**
     * Skips tasks whose onlyIf predicate evaluates to false
     */
    private ExecutionEngine.@Nullable Result executeSkippingOnlyIf(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Spec<? super TaskInternal> unsatisfiedSpec = null;
        try {
            Spec<? super TaskInternal> onlyIf = task.getOnlyIf();
            // Some third-party plugins override getOnlyIf, returning a generic Spec
            if (onlyIf instanceof DescribingAndSpec) {
                DescribingAndSpec<? super TaskInternal> describingAndSpec = Cast.uncheckedCast(onlyIf);
                unsatisfiedSpec = describingAndSpec.findUnsatisfiedSpec(task);
            } else {
                if (!onlyIf.isSatisfiedBy(task)) {
                    unsatisfiedSpec = onlyIf;
                }
            }
        } catch (Throwable t) {
            state.setOutcome(new GradleException(String.format("Could not evaluate onlyIf predicate for %s.", task), t));
            return null;
        }

        if (unsatisfiedSpec != null) {
            if (unsatisfiedSpec instanceof SelfDescribingSpec) {
                SelfDescribingSpec<? super TaskInternal> selfDescribingSpec = Cast.uncheckedCast(unsatisfiedSpec);
                LOGGER.info("Skipping {} as task onlyIf '{}' is false.", task, selfDescribingSpec.getDisplayName());
                state.setSkipReasonMessage("'" + selfDescribingSpec.getDisplayName() + "' not satisfied");
            } else {
                LOGGER.info("Skipping {} as task onlyIf is false.", task);
                state.setSkipReasonMessage("onlyIf not satisfied");
            }
            state.setOutcome(TaskExecutionOutcome.SKIPPED);
            return null;
        }

        return executeSkippingWithNoActions(task, state, context);
    }

    /**
     * Skips tasks that have no actions.
     */
    private ExecutionEngine.@Nullable Result executeSkippingWithNoActions(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (!task.hasTaskActions()) {
            LOGGER.info("Skipping {} as it has no actions.", task);
            boolean upToDate = true;
            for (Task dependency : taskExecutionGraph.getDependencies(task)) {
                if (!dependency.getState().getSkipped()) {
                    upToDate = false;
                    break;
                }
            }
            state.setActionable(false);
            state.setOutcome(upToDate ? TaskExecutionOutcome.UP_TO_DATE : TaskExecutionOutcome.EXECUTED);
            return null;
        }
        return executeFinalizingProperties(task, state, context);
    }

    /**
     * Notifies the task properties of the start and completion of task execution, so they may finalize
     * and cache whatever state is required to efficiently fingerprint inputs and outputs or apply validation.
     *
     * Currently, this is applied prior to validation, so that all properties are finalized before their value
     * is validated. However, we should finalize and validate any property whose value is used to finalize the
     * value of another property.
     */
    private ExecutionEngine.@Nullable Result executeFinalizingProperties(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskProperties properties = context.getTaskProperties();
        for (LifecycleAwareValue value : properties.getLifecycleAwareValues()) {
            value.prepareValue();
        }
        try {
            return executeResolvingExecutionMode(task, state, context);
        } finally {
            for (LifecycleAwareValue value : properties.getLifecycleAwareValues()) {
                value.cleanupValue();
            }
        }
    }

    private ExecutionEngine.@Nullable Result executeResolvingExecutionMode(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        context.setTaskExecutionMode(executionModeResolver.getExecutionMode(task, context.getTaskProperties()));
        try {
            return executeTrackingTaskIdentity(task, state, context);
        } finally {
            context.setTaskExecutionMode(null);
        }
    }

    /**
     * Notifies the Problems API about which tasks is being executed.
     */
    private ExecutionEngine.@Nullable Result executeTrackingTaskIdentity(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        try {
            ProblemTaskIdentityTracker.setTaskIdentity(new TaskIdentity(task.getTaskIdentity().getBuildTreePath().asString()));
            return doExecute(task, state, context);
        } finally {
            ProblemTaskIdentityTracker.clear();
        }
    }

    private ExecutionEngine.@Nullable Result doExecute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskExecution work = new TaskExecution(
            task,
            context,
            actionListener,
            asyncWorkTracker,
            buildOperationRunner,
            classLoaderHierarchyHasher,
            executionHistoryStore,
            fileCollectionFactory,
            fileResolver,
            inputFingerprinter,
            listenerManager,
            reservedFileSystemLocationRegistry,
            taskCacheabilityResolver,
            taskDependencyFactory,
            missingTaskDependencyDetector
        );

        try {
            ExecutionEngine.Request request = executionEngine.createRequest(work);
            context.getTaskExecutionMode().getRebuildReason().ifPresent(request::forceNonIncremental);
            request.withValidationContext(context.getValidationContext());
            ExecutionEngine.Result result = request.execute();
            result.getExecution().ifSuccessfulOrElse(
                success -> state.setOutcome(convertOutcome(success.getOutcome())),
                failure -> state.setOutcome(new TaskExecutionException(task, failure))
            );
            return result;
        } catch (WorkValidationException ex) {
            state.setOutcome(ex);
            return null;
        }
    }

    private static TaskExecutionOutcome convertOutcome(ExecutionOutcome model) {
        switch (model) {
            case FROM_CACHE:
                return TaskExecutionOutcome.FROM_CACHE;
            case UP_TO_DATE:
                return TaskExecutionOutcome.UP_TO_DATE;
            case SHORT_CIRCUITED:
                return TaskExecutionOutcome.NO_SOURCE;
            case EXECUTED_INCREMENTALLY:
            case EXECUTED_NON_INCREMENTALLY:
                return TaskExecutionOutcome.EXECUTED;
            default:
                throw new AssertionError();
        }
    }

}
