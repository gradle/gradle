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
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.buildoption.StringInternalOption;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLimits;

import java.util.concurrent.LinkedBlockingQueue;

public class TaskPathProjectEvaluator implements ProjectConfigurer {

    /**
     * Parallel project configuration scheduling strategy, one of:
     * <ul>
     * <li> {@code aot}: schedule all projects ahead-of-time and let them compete for resources;
     * <li> {@code jit}: schedule children only after their parents have been configured (just-in-time);
     * </ul>
     * Default is {@code jit}.
     */
    private static final StringInternalOption PARALLEL_CONFIGURATION_SCHEDULER =
        new StringInternalOption("org.gradle.internal.isolated-projects.scheduler", "jit");

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
    public void configure(ProjectInternal project) {
        project.getOwner().ensureConfigured();
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
    public void configureHierarchy(ProjectInternal project) {
        configure(project);
        for (Project sub : project.getSubprojects()) {
            configure((ProjectInternal) sub);
        }
    }

    @Override
    public void configureHierarchyInParallel(ProjectInternal project) {
        assert project.getParent() == null : "Parallel configuration must start from root!";

        if (maxWorkerCount() < 2) {
            // We need at least two workers to configure in parallel
            configureHierarchy(project);
            return;
        }

        final ProjectState root = project.getOwner();
        if (!root.hasChildren()) {
            // No hierarchy to configure
            root.ensureConfigured();
            return;
        }

        String strategy = schedulingStrategy();
        if (strategy.equals("jit")) {
            scheduleProjectsJustInTime(root);
        } else {
            scheduleProjectsAheadOfTime(project);
        }
    }

    private String schedulingStrategy() {
        return internalOptions.getOption(PARALLEL_CONFIGURATION_SCHEDULER).get();
    }

    private void scheduleProjectsAheadOfTime(ProjectInternal root) {
        runAllWithAccessToProjectState(queue -> {
            for (Project p : root.getAllprojects()) {
                queue.add(configureOperationFor(p));
            }
        });
    }

    private void scheduleProjectsJustInTime(ProjectState root) {
        assert maxWorkerCount() > 1 : "Parallel traversal requires more than one worker!";
        runAllWithAccessToProjectState(queue -> {

            final LinkedBlockingQueue<ProjectState> readyQueue = new LinkedBlockingQueue<>();
            queue.add(traverseProject(root, readyQueue));

            int pending = root.hasChildren() ? 1 : 0;
            while (pending > 0) {
                ProjectState next;
                try {
                    next = readyQueue.take();
                    --pending;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (final ProjectState child : next.getUnorderedChildProjects()) {
                    queue.add(traverseProject(child, readyQueue));
                    if (child.hasChildren()) {
                        // Only wait for projects that have children to be configured
                        ++pending;
                    }
                }
            }
        });
    }

    private static RunnableBuildOperation traverseProject(ProjectState project, LinkedBlockingQueue<ProjectState> readyQueue) {
        return new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                try {
                    project.ensureSelfConfigured();
                } finally {
                    if (project.hasChildren()) {
                        // Only enqueue projects that have children to be configured
                        readyQueue.add(project);
                    }
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

    private RunnableBuildOperation configureOperationFor(Project p) {
        return new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                configure((ProjectInternal) p);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Configure project " + p.getName());
            }
        };
    }

    private int maxWorkerCount() {
        return workerLimits.getMaxWorkerCount();
    }
}
