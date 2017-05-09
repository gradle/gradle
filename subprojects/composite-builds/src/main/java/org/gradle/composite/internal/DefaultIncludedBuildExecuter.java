/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.IncludedBuilds;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DefaultIncludedBuildExecuter implements IncludedBuildExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildExecuter.class);
    private final IncludedBuilds includedBuilds;

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition buildCompleted = lock.newCondition();
    private final List<BuildRequest> executingBuilds = Lists.newLinkedList();

    // Not guarded by lock: possible thread-safety issue
    private final Multimap<BuildIdentifier, String> executedTasks = LinkedHashMultimap.create();

    public DefaultIncludedBuildExecuter(IncludedBuilds includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    @Override
    public void execute(BuildIdentifier sourceBuild, final BuildIdentifier targetBuild, final Iterable<String> taskNames) {
        BuildRequest buildRequest = new BuildRequest(sourceBuild, targetBuild, taskNames);
        buildStarted(buildRequest);
        try {
            doBuild(targetBuild, taskNames);
        } finally {
            buildCompleted(buildRequest);
        }
    }

    private void buildStarted(BuildRequest buildRequest) {
        lock.lock();
        try {
            List<BuildIdentifier> candidateCycle = Lists.newArrayList();
            checkNoCycles(buildRequest, buildRequest.targetBuild, candidateCycle);
            waitForExistingBuildToComplete(buildRequest.targetBuild);
            executingBuilds.add(buildRequest);
        } finally {
            lock.unlock();
        }
    }

    private void checkNoCycles(BuildRequest buildRequest, BuildIdentifier target, List<BuildIdentifier> candidateCycle) {
        candidateCycle.add(target);
        for (BuildRequest executingBuild : executingBuilds) {
            if (executingBuild.requestingBuild.equals(target)) {
                BuildIdentifier nextTarget = executingBuild.targetBuild;

                if (nextTarget.equals(buildRequest.requestingBuild)) {
                    candidateCycle.add(nextTarget);
                    ProjectComponentSelector selector = DefaultProjectComponentSelector.newSelector(buildRequest.targetBuild, ":");
                    throw new ModuleVersionResolveException(selector, "Included build dependency cycle: " + reportCycle(candidateCycle));
                }

                checkNoCycles(buildRequest, nextTarget, candidateCycle);
            }
        }
        candidateCycle.remove(target);
    }

    private void waitForExistingBuildToComplete(BuildIdentifier buildId) {
        try {
            while (buildInProgress(buildId)) {
                buildCompleted.await();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean buildInProgress(BuildIdentifier buildId) {
        for (BuildRequest executingBuild : executingBuilds) {
            if (executingBuild.targetBuild.equals(buildId)) {
                return true;
            }
        }
        return false;
    }

    private String reportCycle(List<BuildIdentifier> cycle) {
        StringBuilder cycleReport = new StringBuilder();
        for (BuildIdentifier buildIdentifier : cycle) {
            cycleReport.append(buildIdentifier);
            cycleReport.append(" -> ");
        }
        cycleReport.append(cycle.get(0));
        return cycleReport.toString();
    }

    private void buildCompleted(BuildRequest buildRequest) {
        lock.lock();
        try {
            executingBuilds.remove(buildRequest);
            buildCompleted.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void doBuild(BuildIdentifier buildId, Iterable<String> taskPaths) {
        List<String> tasksToExecute = Lists.newArrayList();
        for (String taskPath : taskPaths) {
            if (executedTasks.put(buildId, taskPath)) {
                tasksToExecute.add(taskPath);
            }
        }
        if (tasksToExecute.isEmpty()) {
            return;
        }
        LOGGER.info("Executing " + buildId.getName() + " tasks " + taskPaths);

        IncludedBuildInternal build = (IncludedBuildInternal) includedBuilds.getBuild(buildId.getName());
        build.execute(tasksToExecute);
    }

    private class BuildRequest {
        final BuildIdentifier requestingBuild;
        final BuildIdentifier targetBuild;
        final Iterable<String> tasks;

        public BuildRequest(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, Iterable<String> tasks) {
            this.requestingBuild = requestingBuild;
            this.targetBuild = targetBuild;
            this.tasks = tasks;
        }
    }

}
