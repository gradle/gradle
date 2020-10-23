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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;

import java.util.Collection;


public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph {
    private final IncludedBuildControllers includedBuilds;

    public DefaultIncludedBuildTaskGraph(IncludedBuildControllers includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    @Override
    public synchronized void addTask(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, String taskPath) {
        buildControllerFor(targetBuild).queueForExecution(taskPath);
    }

    @Override
    public void awaitTaskCompletion(Collection<? super Throwable> taskFailures) {
        // Start task execution if necessary: this is required for building plugin artifacts,
        // since these are built on-demand prior to the regular start signal for included builds.
        includedBuilds.populateTaskGraphs();
        includedBuilds.startTaskExecution();
        includedBuilds.awaitTaskCompletion(taskFailures);
    }

    @Override
    public IncludedBuildTaskResource.State getTaskState(BuildIdentifier targetBuild, String taskPath) {
        return buildControllerFor(targetBuild).getTaskState(taskPath);
    }

    @Override
    public TaskInternal getTask(BuildIdentifier targetBuild, String taskPath) {
        return buildControllerFor(targetBuild).getTask(taskPath);
    }

    private IncludedBuildController buildControllerFor(BuildIdentifier buildId) {
        return includedBuilds.getBuildController(buildId);
    }

}
