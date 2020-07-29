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
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.internal.TaskInputsListeners;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionModeResolver;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter;
import org.gradle.api.internal.tasks.execution.CleanupStaleOutputsExecuter;
import org.gradle.api.internal.tasks.execution.DefaultEmptySourceTaskSkipper;
import org.gradle.api.internal.tasks.execution.DefaultTaskCacheabilityResolver;
import org.gradle.api.internal.tasks.execution.DefaultTaskSnapshotter;
import org.gradle.api.internal.tasks.execution.EmptySourceTaskSkipper;
import org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter;
import org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter;
import org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter;
import org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter;
import org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter;
import org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter;
import org.gradle.api.internal.tasks.execution.TaskCacheabilityResolver;
import org.gradle.api.internal.tasks.execution.TaskSnapshotter;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.file.DefaultReservedFileSystemLocationRegistry;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.file.ReservedFileSystemLocation;
import org.gradle.internal.file.ReservedFileSystemLocationRegistry;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;

import java.util.List;

public class ProjectExecutionServices extends DefaultServiceRegistry {

    public ProjectExecutionServices(ProjectInternal project) {
        super("Configured project services for '" + project.getPath() + "'", project.getServices());
    }

    TaskActionListener createTaskActionListener(ListenerManager listenerManager) {
        return listenerManager.getBroadcaster(TaskActionListener.class);
    }

    TaskCacheabilityResolver createTaskCacheabilityResolver(RelativeFilePathResolver relativeFilePathResolver) {
        return new DefaultTaskCacheabilityResolver(relativeFilePathResolver);
    }

    ReservedFileSystemLocationRegistry createReservedFileLocationRegistry(List<ReservedFileSystemLocation> reservedFileSystemLocations) {
        return new DefaultReservedFileSystemLocationRegistry(reservedFileSystemLocations);
    }

    EmptySourceTaskSkipper createEmptySourceTaskSkipper(
        BuildOutputCleanupRegistry buildOutputCleanupRegistry,
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        TaskInputsListeners taskInputsListeners
    ) {
        return new DefaultEmptySourceTaskSkipper(
            buildOutputCleanupRegistry,
            deleter,
            outputChangeListener,
            taskInputsListeners
        );
    }

    TaskExecuter createTaskExecuter(
        AsyncWorkTracker asyncWorkTracker,
        BuildCacheController buildCacheController,
        BuildOperationExecutor buildOperationExecutor,
        BuildOutputCleanupRegistry cleanupRegistry,
        GradleEnterprisePluginManager gradleEnterprisePluginManager,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        Deleter deleter,
        EmptySourceTaskSkipper emptySourceTaskSkipper,
        ExecutionHistoryStore executionHistoryStore,
        FileCollectionFactory fileCollectionFactory,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        FileOperations fileOperations,
        ListenerManager listenerManager,
        OutputChangeListener outputChangeListener,
        OutputFilesRepository outputFilesRepository,
        PropertyWalker propertyWalker,
        ReservedFileSystemLocationRegistry reservedFileSystemLocationRegistry,
        StartParameter startParameter,
        TaskActionListener actionListener,
        TaskCacheabilityResolver taskCacheabilityResolver,
        TaskExecutionGraphInternal taskExecutionGraph,
        TaskExecutionListener taskExecutionListener,
        TaskExecutionModeResolver repository,
        TaskListenerInternal taskListenerInternal,
        TaskSnapshotter taskSnapshotter,
        WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor
    ) {
        TaskExecuter executer = new ExecuteActionsTaskExecuter(
            buildCacheController.isEnabled()
                ? ExecuteActionsTaskExecuter.BuildCacheState.ENABLED
                : ExecuteActionsTaskExecuter.BuildCacheState.DISABLED,
            gradleEnterprisePluginManager.isPresent()
                ? ExecuteActionsTaskExecuter.ScanPluginState.APPLIED
                : ExecuteActionsTaskExecuter.ScanPluginState.NOT_APPLIED,
            taskSnapshotter,
            executionHistoryStore,
            buildOperationExecutor,
            asyncWorkTracker,
            actionListener,
            taskCacheabilityResolver,
            fingerprinterRegistry,
            classLoaderHierarchyHasher,
            workExecutor,
            listenerManager,
            reservedFileSystemLocationRegistry,
            emptySourceTaskSkipper,
            fileCollectionFactory,
            fileOperations
        );
        executer = new CleanupStaleOutputsExecuter(
            buildOperationExecutor,
            cleanupRegistry,
            deleter,
            outputChangeListener,
            outputFilesRepository,
            executer
        );
        executer = new FinalizePropertiesTaskExecuter(executer);
        executer = new ResolveTaskExecutionModeExecuter(repository, fileCollectionFactory, propertyWalker, executer);
        executer = new SkipTaskWithNoActionsExecuter(taskExecutionGraph, executer);
        executer = new SkipOnlyIfTaskExecuter(executer);
        executer = new CatchExceptionTaskExecuter(executer);
        executer = new EventFiringTaskExecuter(buildOperationExecutor, taskExecutionListener, taskListenerInternal, executer);
        return executer;
    }

    // Overrides the global ClasspathFingerPrinter, currently need to have the parent parameter
    ClasspathFingerprinter createClasspathFingerprinter(ClasspathFingerprinter parent, ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner, InputNormalizationHandlerInternal inputNormalizationHandler) {
        return new DefaultClasspathFingerprinter(
            resourceSnapshotterCacheService,
            fileCollectionSnapshotter,
            inputNormalizationHandler.getRuntimeClasspath().getClasspathResourceFilter(),
            inputNormalizationHandler.getRuntimeClasspath().getManifestAttributeResourceEntryFilter(),
            inputNormalizationHandler.getRuntimeClasspath().getManifestPropertyResourceEntryFilter(),
            stringInterner
        );
    }

    TaskSnapshotter createTaskFingerprinter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new DefaultTaskSnapshotter(fileCollectionSnapshotter);
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(List<FileCollectionFingerprinter> fingerprinters) {
        return new DefaultFileCollectionFingerprinterRegistry(fingerprinters);
    }

    TaskExecutionModeResolver createExecutionModeResolver(
        StartParameter startParameter
    ) {
        return new DefaultTaskExecutionModeResolver(startParameter);
    }
}
