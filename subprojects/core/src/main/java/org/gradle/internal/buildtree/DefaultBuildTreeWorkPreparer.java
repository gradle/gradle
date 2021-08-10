/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.internal.build.BuildLifecycleController;

public class DefaultBuildTreeWorkPreparer implements BuildTreeWorkPreparer {
    private final BuildLifecycleController buildController;
    private final IncludedBuildTaskGraph includedBuildTaskGraph;

    public DefaultBuildTreeWorkPreparer(BuildLifecycleController buildLifecycleController, IncludedBuildTaskGraph includedBuildTaskGraph) {
        this.buildController = buildLifecycleController;
        this.includedBuildTaskGraph = includedBuildTaskGraph;
    }

    @Override
    public void scheduleRequestedTasks() {
        buildController.prepareToScheduleTasks();
        includedBuildTaskGraph.prepareTaskGraph(() -> {
            buildController.scheduleRequestedTasks();
            includedBuildTaskGraph.populateTaskGraphs();
            buildController.finalizeWorkGraph(true);
        });
    }
}
