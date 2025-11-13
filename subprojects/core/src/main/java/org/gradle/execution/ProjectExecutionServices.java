/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionModeResolver;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter;
import org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies;
import org.gradle.execution.plan.MissingTaskDependencyDetector;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.impl.DefaultInputFingerprinter;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.impl.FileCollectionFingerprinterRegistrations;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.normalization.internal.RuntimeClasspathNormalizationInternal;

public class ProjectExecutionServices implements ServiceRegistrationProvider {

    public static CloseableServiceRegistry create(
        ServiceRegistry buildServices,
        FileResolver fileResolver,
        RuntimeClasspathNormalizationInternal runtimeClasspathNormalization,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry
    ) {
        return ServiceRegistryBuilder.builder()
            .displayName("Execution services")
            .parent(buildServices)
            .provider(new ProjectExecutionServices(
                fileResolver,
                runtimeClasspathNormalization,
                reservedFileSystemLocationRegistry
            ))
            .build();
    }

    private final FileResolver fileResolver;
    private final RuntimeClasspathNormalizationInternal runtimeClasspathNormalization;
    private final ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry;

    public ProjectExecutionServices(
        FileResolver fileResolver,
        RuntimeClasspathNormalizationInternal runtimeClasspathNormalization,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry
    ) {
        this.fileResolver = fileResolver;
        this.runtimeClasspathNormalization = runtimeClasspathNormalization;
        this.reservedFileSystemLocationRegistry = reservedFileSystemLocationRegistry;
    }

    @Provides
    MissingTaskDependencyDetector createMissingTaskDependencyDetector(ExecutionNodeAccessHierarchies hierarchies) {
        return new MissingTaskDependencyDetector(hierarchies.getOutputHierarchy(), hierarchies.createInputHierarchy());
    }

    @Provides
    TaskExecuter createTaskExecuter(
        AsyncWorkTracker asyncWorkTracker,
        BuildOperationRunner buildOperationRunner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionHistoryStore executionHistoryStore,
        ListenerManager listenerManager,
        TaskExecutionGraphInternal taskExecutionGraph,
        StartParameter startParameter,
        ExecutionEngine executionEngine,
        InputFingerprinter inputFingerprinter,
        MissingTaskDependencyDetector missingTaskDependencyDetector
    ) {
        TaskExecuter executer = new ExecuteActionsTaskExecuter(
            executionHistoryStore,
            buildOperationRunner,
            asyncWorkTracker,
            classLoaderHierarchyHasher,
            executionEngine,
            inputFingerprinter,
            listenerManager,
            reservedFileSystemLocationRegistry,
            fileResolver,
            missingTaskDependencyDetector
        );
        executer = new ProblemsTaskPathTrackingTaskExecuter(executer);
        executer = new FinalizePropertiesTaskExecuter(executer);
        executer = new ResolveTaskExecutionModeExecuter(new DefaultTaskExecutionModeResolver(startParameter), executer);
        executer = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(
            buildOperationRunner, taskExecutionGraph.getLegacyTaskListenerBroadcast(), listenerManager.getBroadcaster(TaskListenerInternal.class), executer);
        return executer;
    }

    @Provides
    InputFingerprinter createInputFingerprinter(
        FileCollectionSnapshotter snapshotter,
        StringInterner stringInterner,
        ResourceSnapshotterCacheService resourceSnapshotterCacheService,
        ValueSnapshotter valueSnapshotter
    ) {
        FileCollectionFingerprinterRegistry fingerprinterRegistry = new DefaultFileCollectionFingerprinterRegistry(
            new FileCollectionFingerprinterRegistrations(
                stringInterner,
                resourceSnapshotterCacheService,
                runtimeClasspathNormalization.getClasspathResourceFilter(),
                runtimeClasspathNormalization.getManifestAttributeResourceEntryFilter(),
                runtimeClasspathNormalization.getPropertiesFileFilters()
            ).getRegistrants()
        );

        return new DefaultInputFingerprinter(snapshotter, fingerprinterRegistry, valueSnapshotter);
    }

}
