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
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.IncludedBuilds;
import org.gradle.internal.UncheckedException;
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
    private final List<BuildIdentifier> executingBuilds = Lists.newLinkedList();

    // Not guarded by lock: possible thread-safety issue
    private final Multimap<BuildIdentifier, String> executedTasks = LinkedHashMultimap.create();

    public DefaultIncludedBuildExecuter(IncludedBuilds includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    @Override
    public void execute(final BuildIdentifier targetBuild, final Iterable<String> taskNames) {
        buildStarted(targetBuild);
        try {
            doBuild(targetBuild, taskNames);
        } finally {
            buildCompleted(targetBuild);
        }
    }

    private void buildStarted(BuildIdentifier targetBuild) {
        lock.lock();
        try {
            waitForExistingBuildToComplete(targetBuild);
            executingBuilds.add(targetBuild);
        } finally {
            lock.unlock();
        }
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
        return executingBuilds.contains(buildId);
    }

    private void buildCompleted(BuildIdentifier targetBuild) {
        lock.lock();
        try {
            executingBuilds.remove(targetBuild);
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
}
