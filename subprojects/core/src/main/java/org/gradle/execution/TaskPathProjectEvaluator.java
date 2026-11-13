/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.project.ProjectOrderingUtil;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLimits;

public class TaskPathProjectEvaluator implements ProjectConfigurer {

    /**
     * Parallel project configuration scheduling strategy, one of:
     * <ul>
     * <li> {@code aot}: schedule all projects ahead-of-time and let them compete for resources;
     * <li> {@code jit}: schedule children only after their parents have been configured (just-in-time);
     * </ul>
     * Default is {@code jit}.
     */
    private static final InternalOption<String> PARALLEL_CONFIGURATION_SCHEDULER =
        InternalOptions.ofString("org.gradle.internal.isolated-projects.scheduler", "jit");

    private final BuildCancellationToken cancellationToken;
    private final BuildOperationExecutor buildOperationExecutor;
    private final WorkerLimits workerLimits;
    private final InternalOptions internalOptions;

    public TaskPathProjectEvaluator(
        BuildCancellationToken cancellationToken,
        BuildOperationExecutor buildOperationExecutor,
        WorkerLimits workerLimits,
        InternalOptions internalOptions
    ) {
        this.cancellationToken = cancellationToken;
        this.buildOperationExecutor = buildOperationExecutor;
        this.workerLimits = workerLimits;
        this.internalOptions = internalOptions;
    }

    @Override
    public void configureFully(ProjectState projectState) {
        projectState.ensureConfigured();
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException();
        }
        projectState.ensureTasksDiscovered();
    }

    @Override
    public void configureHierarchy(ProjectState projectState) {
        for (ProjectState project : ProjectOrderingUtil.orderedAllProjectsOf(projectState)) {
            project.ensureConfigured();
        }
    }

    @Override
    public void configureHierarchyInParallel(ProjectState projectState) {
        if (!projectState.isRootProject()) {
            throw new IllegalArgumentException("Parallel configuration must start from root!");
        }

        if (maxWorkerCount() < 2) {
            // Parallel configuration cannot help with a single worker
            configureHierarchy(projectState);
            return;
        }

        if (!projectState.hasChildren()) {
            // No hierarchy to configure
            projectState.ensureConfigured();
            return;
        }

        String strategy = schedulingStrategy();
        if (strategy.equals("jit")) {
            scheduleProjectsJustInTime(projectState);
        } else {
            scheduleProjectsAheadOfTime(projectState);
        }
    }

    private String schedulingStrategy() {
        return internalOptions.getValue(PARALLEL_CONFIGURATION_SCHEDULER);
    }

    private void scheduleProjectsAheadOfTime(ProjectState root) {
        runAllWithAccessToProjectState(queue -> {
            for (ProjectState p : root.getOwner().getProjects().getAllProjects()) {
                queue.add(configureOperationFor(p));
            }
        });
    }

    private void scheduleProjectsJustInTime(ProjectState root) {
        runAllWithAccessToProjectState(queue -> queue.add(traverseProject(root, queue)));
    }

    private static RunnableBuildOperation traverseProject(ProjectState project, BuildOperationQueue<RunnableBuildOperation> queue) {
        return new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                project.ensureSelfConfigured();
                for (ProjectState child : project.getUnorderedChildProjects()) {
                    queue.add(traverseProject(child, queue));
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Parallelize configuration");
            }
        };
    }

    private void runAllWithAccessToProjectState(Action<BuildOperationQueue<RunnableBuildOperation>> buildOperationQueueAction) {
        try {
            buildOperationExecutor.runAllWithAccessToProjectState(buildOperationQueueAction);
        } catch (MultipleBuildOperationFailures e) {
            if (e.getCauses().size() == 1) {
                throw UncheckedException.throwAsUncheckedException(e.getCauses().get(0));
            }
            throw e;
        }
    }

    private static RunnableBuildOperation configureOperationFor(ProjectState projectState) {
        return new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                projectState.ensureConfigured();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Configure project " + projectState.getName());
            }
        };
    }

    private int maxWorkerCount() {
        return workerLimits.getMaxWorkerCount();
    }
}
