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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.execution.DefaultTaskCacheabilityResolver;
import org.gradle.api.internal.tasks.execution.DefaultTaskNodeExecutor;
import org.gradle.api.internal.tasks.execution.TaskCacheabilityResolver;
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies;
import org.gradle.execution.plan.MissingTaskDependencyDetector;
import org.gradle.execution.plan.TaskNodeExecutor;
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
import org.gradle.internal.file.ReservedFileSystemLocation;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.impl.FileCollectionFingerprinterRegistrations;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;

import java.util.List;

@SuppressWarnings("deprecation")
public class ProjectExecutionServices implements ServiceRegistrationProvider {

    public static CloseableServiceRegistry create(ProjectInternal project) {
        return ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.ProjectExecution.class)
            .displayName("project execution services for '" + project.getPath() + "'")
            .parent(project.getGradle().getServices())
            .provider(new ProjectExecutionServices(project.getServices()))
            .build();
    }

    // The only project state that execution needs, taken explicitly instead of inheriting the project registry
    private final FileResolver fileResolver;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final InputNormalizationHandlerInternal inputNormalizationHandler;
    private final List<ReservedFileSystemLocation> reservedFileSystemLocations;

    private ProjectExecutionServices(ServiceRegistry projectServices) {
        this.fileResolver = projectServices.get(FileResolver.class);
        this.fileCollectionFactory = projectServices.get(FileCollectionFactory.class);
        this.taskDependencyFactory = projectServices.get(TaskDependencyFactory.class);
        this.inputNormalizationHandler = projectServices.get(InputNormalizationHandlerInternal.class);
        this.reservedFileSystemLocations = projectServices.getAll(ReservedFileSystemLocation.class);
    }

    @Provides
    TaskCacheabilityResolver createTaskCacheabilityResolver() {
        return new DefaultTaskCacheabilityResolver(fileResolver);
    }

    @Provides
    ReservedFileSystemLocationRegistry createReservedFileLocationRegistry() {
        return new DefaultReservedFileSystemLocationRegistry(reservedFileSystemLocations);
    }

    @Provides
    MissingTaskDependencyDetector createMissingTaskDependencyDetector(ExecutionNodeAccessHierarchies hierarchies) {
        return new MissingTaskDependencyDetector(hierarchies.getOutputHierarchy(), hierarchies.createInputHierarchy());
    }

    @Provides
    TaskNodeExecutor createTaskNodeExecutor(
        AsyncWorkTracker asyncWorkTracker,
        BuildOperationRunner buildOperationRunner,
        TaskExecutionGraphInternal taskGraph,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ExecutionHistoryStore executionHistoryStore,
        ListenerManager listenerManager,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        TaskCacheabilityResolver taskCacheabilityResolver,
        TaskExecutionModeResolver repository,
        ExecutionEngine executionEngine,
        InputFingerprinter inputFingerprinter,
        MissingTaskDependencyDetector missingTaskDependencyDetector
    ) {
        return new DefaultTaskNodeExecutor(
            buildOperationRunner,
            taskGraph.getLegacyTaskListenerBroadcast(),
            listenerManager.getBroadcaster(TaskListenerInternal.class),
            repository,
            executionHistoryStore,
            asyncWorkTracker,
            listenerManager.getBroadcaster(org.gradle.api.execution.TaskActionListener.class),
            taskCacheabilityResolver,
            classLoaderHierarchyHasher,
            executionEngine,
            inputFingerprinter,
            listenerManager,
            reservedFileSystemLocationRegistry,
            fileCollectionFactory,
            taskDependencyFactory,
            fileResolver,
            missingTaskDependencyDetector
        );
    }

    @Provides
    FileCollectionFingerprinterRegistrations createFileCollectionFingerprinterRegistrations(
        StringInterner stringInterner,
        ResourceSnapshotterCacheService resourceSnapshotterCacheService
    ) {
        return new FileCollectionFingerprinterRegistrations(
            stringInterner,
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
