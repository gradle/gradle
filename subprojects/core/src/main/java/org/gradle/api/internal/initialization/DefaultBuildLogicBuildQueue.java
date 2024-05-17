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
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;

import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultBuildLogicBuildQueue implements BuildLogicBuildQueue {

    private final FileLockManager fileLockManager;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildTreeWorkGraphController buildTreeWorkGraphController;
    private final ReentrantLock lock = new ReentrantLock();

    public DefaultBuildLogicBuildQueue(
        FileLockManager fileLockManager,
        BuildStateRegistry buildStateRegistry,
        BuildTreeWorkGraphController buildTreeWorkGraphController
    ) {
        this.fileLockManager = fileLockManager;
        this.buildStateRegistry = buildStateRegistry;
        this.buildTreeWorkGraphController = buildTreeWorkGraphController;
    }

    @Override
    public <T> T build(BuildState requester, List<TaskIdentifier.TaskBasedTaskIdentifier> tasks, Supplier<T> continuationUnderLock) {
        return tasks.isEmpty()
            ? continuationUnderLock.get() // no resources to be protected
            : withBuildLogicQueueLock(() -> doBuild(tasks, continuationUnderLock));
    }

    @Override
    public <T> T buildBuildSrc(StandAloneNestedBuild buildSrcBuild, Function<BuildTreeLifecycleController, T> continuationUnderLock) {
        return withBuildLogicQueueLock(() -> buildSrcBuild.run(controller -> continuationUnderLock.apply(controller)));
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

    @SuppressWarnings("try")
    private <T> T withBuildLogicQueueLock(Supplier<T> buildAction) {
        lock.lock();
        try {
            final boolean firstLockHolder = lock.getHoldCount() == 1;
            if (firstLockHolder) { // lock file at the top of the callstack only
                try (FileLock ignored = lockBuildLogicQueueFile()) {
                    return buildAction.get();
                }
            }
            return buildAction.get();
        } finally {
            lock.unlock();
        }
    }

    private FileLock lockBuildLogicQueueFile() {
        return fileLockManager.lock(
            new File(rootBuildDir(), ".gradle/noVersion/buildLogic"),
            mode(FileLockManager.LockMode.Exclusive),
            "build logic queue"
        );
    }

    private File rootBuildDir() {
        return buildStateRegistry.getRootBuild().getBuildRootDir();
    }
}
