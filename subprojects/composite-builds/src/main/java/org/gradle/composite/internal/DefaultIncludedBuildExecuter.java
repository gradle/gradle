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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.IncludedBuilds;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

class DefaultIncludedBuildExecuter implements IncludedBuildExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildExecuter.class);

    private final Set<String> executingBuilds = Sets.newHashSet();
    private final Multimap<String, String> executedTasks = LinkedHashMultimap.create();
    private final IncludedBuilds includedBuilds;

    public DefaultIncludedBuildExecuter(IncludedBuilds includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    @Override
    public void execute(ProjectComponentIdentifier buildIdentifier, Iterable<String> taskNames) {
        String buildName = getBuildName(buildIdentifier);
        buildStarted(buildName);
        try {
            doBuild(buildName, taskNames);
        } finally {
            buildCompleted(buildName);
        }
    }

    private String getBuildName(ProjectComponentIdentifier buildIdentifier) {
        if (!buildIdentifier.getProjectPath().endsWith("::")) {
            throw new IllegalArgumentException(buildIdentifier + " is not a build identifier");
        }
        return buildIdentifier.getProjectPath().split("::", 2)[0];
    }

    private synchronized void buildStarted(String build) {
        // Ensure that a particular build is never executing concurrently
        // TODO:DAZ We might need to hold a lock per-build for the parallel build case
        if (!executingBuilds.add(build)) {
            ProjectComponentSelector selector = DefaultProjectComponentSelector.newSelector(build, ":");
            throw new ModuleVersionResolveException(selector, "Dependency cycle including " + selector.getDisplayName());
        }
    }

    private synchronized void buildCompleted(String project) {
        executingBuilds.remove(project);
    }

    private void doBuild(String buildName, Iterable<String> taskPaths) {
        List<String> tasksToExecute = Lists.newArrayList();
        for (String taskPath : taskPaths) {
            if (executedTasks.put(buildName, taskPath)) {
                tasksToExecute.add(taskPath);
            }
        }
        if (tasksToExecute.isEmpty()) {
            return;
        }
        LOGGER.info("Executing " + buildName + " tasks " + taskPaths);

        IncludedBuildInternal build = (IncludedBuildInternal) includedBuilds.getBuild(buildName);
        build.execute(tasksToExecute);
    }

}
