/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;

import java.util.Set;

public class DefaultBuildTreeWorkGraphPreparer implements BuildTreeWorkGraphPreparer {
    private final BuildTaskSelector taskSelector;
    private final BuildStateRegistry buildRegistry;

    public DefaultBuildTreeWorkGraphPreparer(BuildStateRegistry buildRegistry, BuildTaskSelector taskSelector) {
        this.buildRegistry = buildRegistry;
        this.taskSelector = taskSelector;
    }

    @Override
    public void prepareToScheduleTasks(BuildTreeWorkGraph.Builder workGraph) {
        RootBuildState targetBuild = buildRegistry.getRootBuild();
        Set<String> excludedTaskNames = targetBuild.getMutableModel().getStartParameter().getExcludedTaskNames();
        for (String taskName : excludedTaskNames) {
            BuildTaskSelector.Filter filter = taskSelector.resolveExcludedTaskName(targetBuild, taskName);
            workGraph.addFilter(filter.getBuild(), filter.getFilter());
        }
    }
}
