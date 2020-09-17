/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.plan;


import com.google.common.collect.Maps;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.composite.internal.IncludedBuildTaskGraph;
import org.gradle.internal.build.BuildState;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TaskNodeFactory {
    private final Map<Task, TaskNode> nodes = new HashMap<Task, TaskNode>();
    private final IncludedBuildTaskGraph taskGraph;
    private final GradleInternal thisBuild;
    private final BuildIdentifier currentBuildId;
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();

    public TaskNodeFactory(GradleInternal thisBuild, IncludedBuildTaskGraph taskGraph) {
        this.thisBuild = thisBuild;
        currentBuildId = thisBuild.getServices().get(BuildState.class).getBuildIdentifier();
        this.taskGraph = taskGraph;
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public TaskNode getOrCreateNode(Task task) {
        TaskNode node = nodes.get(task);
        if (node == null) {
            if (task.getProject().getGradle() == thisBuild) {
                node = new LocalTaskNode((TaskInternal) task, canonicalizedFileCache);
            } else {
                node = TaskInAnotherBuild.of((TaskInternal) task, currentBuildId, taskGraph);
            }
            nodes.put(task, node);
        }
        return node;
    }

    public void clear() {
        nodes.clear();
    }

}
