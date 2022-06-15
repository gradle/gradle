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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionEngine.Result;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.WorkValidationException;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.AsyncWorkTracker;

import java.util.List;
import java.util.Optional;

/**
 * A {@link TaskExecuter} which executes the actions of a task.
 */
@SuppressWarnings("deprecation")
public class ExecuteActionsTaskExecuter implements TaskExecuter {
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
    private final org.gradle.api.execution.TaskActionListener actionListener;
    private final TaskCacheabilityResolver taskCacheabilityResolver;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ExecutionEngine executionEngine;
    private final InputFingerprinter inputFingerprinter;
    private final ListenerManager listenerManager;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileOperations fileOperations;

    public ExecuteActionsTaskExecuter(
        BuildCacheState buildCacheState,
        ScanPluginState scanPluginState,

        ExecutionHistoryStore executionHistoryStore,
        BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker,
        org.gradle.api.execution.TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionEngine executionEngine,
        InputFingerprinter inputFingerprinter,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
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
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.executionEngine = executionEngine;
        this.inputFingerprinter = inputFingerprinter;
        this.listenerManager = listenerManager;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileOperations = fileOperations;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        boolean emitLegacySnapshottingOperations = buildCacheState == BuildCacheState.ENABLED || scanPluginState == ScanPluginState.APPLIED;
        TaskExecution work = new TaskExecution(
            task,
            context,
            emitLegacySnapshottingOperations,

            actionListener,
            asyncWorkTracker,
            buildOperationExecutor,
            classLoaderHierarchyHasher,
            executionHistoryStore,
            fileCollectionFactory,
            fileOperations,
            inputFingerprinter,
            listenerManager,
            reservedFileSystemLocationRegistry,
            taskCacheabilityResolver
        );
        try {
            return executeIfValid(task, state, context, work);
        } catch (WorkValidationException ex) {
            state.setOutcome(ex);
            return TaskExecuterResult.WITHOUT_OUTPUTS;
        }
    }

    private TaskExecuterResult executeIfValid(TaskInternal task, TaskStateInternal state, TaskExecutionContext context, TaskExecution work) {
        ExecutionEngine.Request request = executionEngine.createRequest(work);
        context.getTaskExecutionMode().getRebuildReason().ifPresent(request::forceNonIncremental);
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
}
