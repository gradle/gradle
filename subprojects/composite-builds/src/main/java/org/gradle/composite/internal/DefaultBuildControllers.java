/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.TaskInAnotherBuild;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultBuildControllers implements BuildControllers {
    // Always iterate over the controllers in a fixed order
    private final Map<Path, BuildController> controllers = new TreeMap<>(idComparator());
    private final ManagedExecutor executorService;
    private final WorkerLeaseService workerLeaseService;
    private final PlanExecutor planExecutor;
    private final int monitoringPollTime;
    private final TimeUnit monitoringPollTimeUnit;

    DefaultBuildControllers(ManagedExecutor executorService, WorkerLeaseService workerLeaseService, PlanExecutor planExecutor, int monitoringPollTime, TimeUnit monitoringPollTimeUnit) {
        this.executorService = executorService;
        this.workerLeaseService = workerLeaseService;
        this.planExecutor = planExecutor;
        this.monitoringPollTime = monitoringPollTime;
        this.monitoringPollTimeUnit = monitoringPollTimeUnit;
    }

    @Override
    public BuildController getBuildController(BuildState build) {
        BuildController buildController = controllers.get(build.getIdentityPath());
        if (buildController != null) {
            return buildController;
        }

        BuildController newBuildController = new DefaultBuildController(build, workerLeaseService);
        controllers.put(build.getIdentityPath(), newBuildController);
        return newBuildController;
    }

    @Override
    public void populateWorkGraphs() {
        // Scheduling the work of a build can discover references to tasks in other builds. The target of each such
        // reference has to be queued in the work graph of the build that owns it, which can in turn discover further
        // references, and so on until no build has anything left to schedule.
        boolean tasksScheduled;
        do {
            queueForeignNodesInOwningGraphs();
            tasksScheduled = scheduleQueuedWork();
        } while (tasksScheduled);

        for (BuildController buildController : controllers.values()) {
            buildController.finalizeWorkGraph();
        }
    }

    /**
     * Queues the target of every reference discovered so far in the work graph of the build that owns it.
     */
    private void queueForeignNodesInOwningGraphs() {
        // Queuing a target can add a controller for a build that is not yet present, so iterate over a copy
        for (BuildController buildController : ImmutableList.copyOf(controllers.values())) {
            for (TaskInAnotherBuild reference : buildController.takeCrossBuildReferences()) {
                BuildState targetBuild = reference.getTargetBuild();
                if (targetBuild == null) {
                    throw new IllegalStateException("No target build is known for " + reference + ".");
                }
                getBuildController(targetBuild).queueForExecution(reference.getTargetNode());
            }
        }
    }

    /**
     * Schedules the work queued for each build, including any build whose controller was just added by
     * {@link #queueForeignNodesInOwningGraphs()}. Scheduling work can discover further references, which is why the
     * caller has to keep queuing and scheduling until there is nothing left to schedule.
     *
     * @return true if any work was scheduled.
     */
    private boolean scheduleQueuedWork() {
        boolean tasksScheduled = false;
        for (BuildController buildController : controllers.values()) {
            if (buildController.scheduleQueuedTasks()) {
                tasksScheduled = true;
            }
        }
        return tasksScheduled;
    }

    @Override
    public ExecutionResult<Void> execute() {
        CountDownLatch complete = new CountDownLatch(controllers.size());
        Map<BuildController, ExecutionResult<Void>> results = new ConcurrentHashMap<>();

        // Start work in each build
        for (BuildController buildController : controllers.values()) {
            buildController.startExecution(executorService, result -> {
                results.put(buildController, result);
                complete.countDown();
            });
        }

        awaitCompletion(complete);

        // Collect the failures in deterministic order
        ExecutionResult<Void> result = ExecutionResult.succeeded();
        for (BuildController buildController : controllers.values()) {
            result = result.withFailures(results.get(buildController));
        }
        return result;
    }

    private void awaitCompletion(CountDownLatch complete) {
        while (true) {
            // Wake for the work in all builds to complete. Periodically wake up and check the executor health

            AtomicBoolean done = new AtomicBoolean();
            // Ensure that this thread does not hold locks while waiting and so prevent this work from completing
            workerLeaseService.blocking(() -> {
                try {
                    done.set(complete.await(monitoringPollTime, monitoringPollTimeUnit));
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            });
            if (done.get()) {
                return;
            }

            planExecutor.assertHealthy();
        }
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(controllers.values()).stop();
    }

    private static Comparator<Path> idComparator() {
        return (path1, path2) -> {
            // Root is always last
            if (path1.equals(Path.ROOT)) {
                if (path2.equals(Path.ROOT)) {
                    return 0;
                } else {
                    return 1;
                }
            }
            if (path2.equals(Path.ROOT)) {
                return -1;
            }
            return path1.compareTo(path2);
        };
    }
}
