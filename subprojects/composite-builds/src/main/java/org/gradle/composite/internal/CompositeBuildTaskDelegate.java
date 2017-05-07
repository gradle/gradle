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

import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.initialization.IncludedBuildTaskGraph;

import java.util.Collection;
import java.util.Set;

public class CompositeBuildTaskDelegate extends DefaultTask {
    private String build;
    private Set<String> tasks = Sets.newLinkedHashSet();

    @Input
    public String getBuild() {
        return build;
    }

    public void setBuild(String build) {
        this.build = build;
    }

    @Input
    public Collection<String> getTasks() {
        return tasks;
    }

    public void addTask(String task) {
        this.tasks.add(task);
    }

    @TaskAction
    public void executeTasksInOtherBuild() {
        IncludedBuildTaskGraph taskGraph = getServices().get(IncludedBuildTaskGraph.class);

        // sourceBuild is currently always root build in a composite
        BuildIdentifier requestingBuild = new DefaultBuildIdentifier(":", true);
        BuildIdentifier targetBuild = new DefaultBuildIdentifier(build);
        taskGraph.addTasks(requestingBuild, targetBuild, tasks);
        taskGraph.awaitCompletion(requestingBuild, targetBuild);
    }
}
