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
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionModeResolver;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.DefaultTaskCacheabilityResolver;
import org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter;
import org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.TaskCacheabilityResolver;
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
import org.gradle.internal.file.DefaultReservedFileSystemLocationRegistry;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.file.ReservedFileSystemLocation;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.impl.FileCollectionFingerprinterRegistrations;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;

import java.util.List;

@SuppressWarnings("deprecation")
public class ProjectExecutionServices implements ServiceRegistrationProvider {

    public static CloseableServiceRegistry create(ProjectInternal project) {
        return ServiceRegistryBuilder.builder()
            .displayName("project execution services for '" + project.getPath() + "'")
            .parent(project.getServices())
            .provider(new ProjectExecutionServices())
            .build();
    }

    @Provides
    org.gradle.api.execution.TaskActionListener createTaskActionListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(org.gradle.api.execution.TaskActionListener.class);
    }

    @Provides
    TaskCacheabilityResolver createTaskCacheabilityResolver(RelativeFilePathResolver relativeFilePathResolver) {
        return new DefaultTaskCacheabilityResolver(relativeFilePathResolver);
    }

    @Provides
    ReservedFileSystemLocationRegistry createReservedFileLocationRegistry(List<ReservedFileSystemLocation> reservedFileSystemLocations) {
        return new DefaultReservedFileSystemLocationRegistry(reservedFileSystemLocations);
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
        FileCollectionFactory fileCollectionFactory,
        TaskDependencyFactory taskDependencyFactory,
        FileOperations fileOperations,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        org.gradle.api.execution.TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        TaskExecutionGraphInternal taskExecutionGraph,
        org.gradle.api.execution.TaskExecutionListener taskExecutionListener,
        TaskExecutionModeResolver repository,
        TaskListenerInternal taskListenerInternal,
        ExecutionEngine executionEngine,
        InputFingerprinter inputFingerprinter
    ) {
        TaskExecuter executer = new ExecuteActionsTaskExecuter(
            executionHistoryStore,
            buildOperationRunner,
            asyncWorkTracker,
            actionListener,
            taskCacheabilityResolver,
            classLoaderHierarchyHasher,
            executionEngine,
            inputFingerprinter,
            listenerManager,
            reservedFileSystemLocationRegistry,
            fileCollectionFactory,
            taskDependencyFactory,
            // TODO Can we inject a PathToFileResolver here directly?
            fileOperations.getFileResolver()
        );
        executer = new ProblemsTaskPathTrackingTaskExecuter(executer);
        executer = new FinalizePropertiesTaskExecuter(executer);
        executer = new ResolveTaskExecutionModeExecuter(repository, executer);
        executer = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(buildOperationRunner, taskExecutionListener, taskListenerInternal, executer);
        return executer;
    }

    @Provides
    FileCollectionFingerprinterRegistrations createFileCollectionFingerprinterRegistrations(
        StringInterner stringInterner,
        FileCollectionSnapshotter fileCollectionSnapshotter,
        ResourceSnapshotterCacheService resourceSnapshotterCacheService,
        InputNormalizationHandlerInternal inputNormalizationHandler
    ) {
        return new FileCollectionFingerprinterRegistrations(
            stringInterner,
            fileCollectionSnapshotter,
            resourceSnapshotterCacheService,
            inputNormalizationHandler.getRuntimeClasspath().getClasspathResourceFilter(),
            inputNormalizationHandler.getRuntimeClasspath().getManifestAttributeResourceEntryFilter(),
            inputNormalizationHandler.getRuntimeClasspath().getPropertiesFileFilters()
        );
    }

    @Provides
    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(FileCollectionFingerprinterRegistrations fileCollectionFingerprinterRegistrations) {
        return new DefaultFileCollectionFingerprinterRegistry(fileCollectionFingerprinterRegistrations.getRegistrants());
    }

    @Provides
    InputFingerprinter createInputFingerprinter(
        FileCollectionSnapshotter snapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ValueSnapshotter valueSnapshotter
    ) {
        return new DefaultInputFingerprinter(snapshotter, fingerprinterRegistry, valueSnapshotter);
    }

    @Provides
    TaskExecutionModeResolver createExecutionModeResolver(
        StartParameter startParameter
    ) {
        return new DefaultTaskExecutionModeResolver(startParameter);
    }
}
