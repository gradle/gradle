/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.work.Synchronizer;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode;

public class DefaultBuildLogicBuildQueue implements BuildLogicBuildQueue {

    private final BuildOperationRunner runner;
    private final FileLockManager fileLockManager;
    private final BuildTreeWorkGraphController buildTreeWorkGraphController;
    private final ProjectCacheDir projectCacheDir;
    private final Synchronizer resource;
    private FileLock fileLock = null;

    public DefaultBuildLogicBuildQueue(
        BuildOperationRunner runner,
        FileLockManager fileLockManager,
        BuildTreeWorkGraphController buildTreeWorkGraphController,
        ProjectCacheDir projectCacheDir,
        WorkerLeaseService workerLeaseService
    ) {
        this.runner = runner;
        this.fileLockManager = fileLockManager;
        this.buildTreeWorkGraphController = buildTreeWorkGraphController;
        this.projectCacheDir = projectCacheDir;
        this.resource = workerLeaseService.newResource();
    }

    @Override
    public <T> T build(BuildState requester, List<TaskIdentifier.TaskBasedTaskIdentifier> tasks, Supplier<T> continuationUnderLock) {
        if (tasks.isEmpty()) {
            // no resources to be protected
            return continuationUnderLock.get();
        }
        List<TaskIdentifier.TaskBasedTaskIdentifier> remaining = removeExecuted(tasks);
        if (remaining.isEmpty()) {
            // all tasks already executed
            return continuationUnderLock.get();
        }
        return withBuildLogicQueueLockInBuildOperation(
            "Run included build logic build for " + nameOf(requester),
            () -> doBuild(tasks, continuationUnderLock)
        );
    }

    @Override
    public <T> T buildBuildSrc(StandAloneNestedBuild buildSrcBuild, Function<BuildTreeLifecycleController, T> continuationUnderLock) {
        // This function is already called inside the build operation.
        return withBuildLogicQueueLock(() -> buildSrcBuild.run(continuationUnderLock));
    }

    private <T> T doBuild(List<TaskIdentifier.TaskBasedTaskIdentifier> tasks, Supplier<T> continuationUnderLock) {
        buildTreeWorkGraphController.withNewWorkGraph(graph -> {
            graph
                .scheduleWork(builder -> builder.scheduleTasks(tasks))
                .runWork()
                .rethrow();
            return null;
        });
        return continuationUnderLock.get();
    }

    private <T> T withBuildLogicQueueLockInBuildOperation(String buildOperationDescription, Supplier<T> buildAction) {
        return runner.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                return withBuildLogicQueueLock(buildAction);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(buildOperationDescription);
            }
        });
    }

    private <T> T withBuildLogicQueueLock(Supplier<T> buildAction) {
        return resource.withLock(() -> {
            if (fileLock == null) { // lock file at the top of the callstack only
                try (FileLock fileLock = lockBuildLogicQueueFile()) {
                    this.fileLock = fileLock;
                    try {
                        return buildAction.get();
                    } finally {
                        this.fileLock = null;
                    }
                }
            }
            return buildAction.get();
        });
    }

    private FileLock lockBuildLogicQueueFile() {
        return fileLockManager.lock(
            new File(projectCacheDir.getDir(), "noVersion/buildLogic"),
            mode(FileLockManager.LockMode.Exclusive),
            "build logic queue"
        );
    }

    private static List<TaskIdentifier.TaskBasedTaskIdentifier> removeExecuted(List<TaskIdentifier.TaskBasedTaskIdentifier> tasks) {
        return tasks.stream()
            .filter(identifier -> !identifier.getTask().getState().getExecuted())
            .collect(Collectors.toList());
    }

    private static String nameOf(BuildState build) {
        return build.getDisplayName().getDisplayName();
    }
}
