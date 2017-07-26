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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.Path;

import java.util.List;

public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph {
    private final Multimap<BuildIdentifier, BuildIdentifier> buildDependencies = LinkedHashMultimap.create();
    private final IncludedBuildControllers includedBuilds;

    public DefaultIncludedBuildTaskGraph(IncludedBuildControllers includedBuilds) {
            this.includedBuilds = includedBuilds;
        }

    @Override
    public synchronized void addTask(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, String taskPath) {
        boolean newBuildDependency = buildDependencies.put(requestingBuild, targetBuild);
        if (newBuildDependency) {
            List<BuildIdentifier> candidateCycle = Lists.newArrayList();
            checkNoCycles(requestingBuild, targetBuild, candidateCycle);
        }

        getBuildController(targetBuild).queueForExecution(taskPath);
    }

    @Override
    public void awaitCompletion(BuildIdentifier targetBuild, String taskPath) {
        // Start task execution if necessary: this is required for building plugin artifacts,
        // since these are built on-demand prior to the regular start signal for included builds.
        includedBuilds.startTaskExecution();

        getBuildController(targetBuild).awaitCompletion(taskPath);
    }

    public boolean isComplete(BuildIdentifier targetBuild, String taskPath) {
        IncludedBuildController controller = getBuildController(targetBuild);
        return controller.isComplete(taskPath);
    }

    private IncludedBuildController getBuildController(BuildIdentifier buildId) {
        return includedBuilds.getBuildController(buildId);
    }

    private void checkNoCycles(BuildIdentifier sourceBuild, BuildIdentifier targetBuild, List<BuildIdentifier> candidateCycle) {
        candidateCycle.add(targetBuild);
        for (BuildIdentifier nextTarget : buildDependencies.get(targetBuild)) {
            if (sourceBuild.equals(nextTarget)) {
                candidateCycle.add(nextTarget);
                ProjectComponentSelector selector = DefaultProjectComponentSelector.newSelector(candidateCycle.get(0), Path.ROOT.getPath());
                throw new ModuleVersionResolveException(selector, "Included build dependency cycle: " + reportCycle(candidateCycle));
            }

            checkNoCycles(sourceBuild, nextTarget, candidateCycle);

        }
        candidateCycle.remove(targetBuild);
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
}
