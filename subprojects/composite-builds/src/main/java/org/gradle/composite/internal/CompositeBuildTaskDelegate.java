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

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

public class CompositeBuildTaskDelegate extends DefaultTask implements IncludedBuildTaskResource {
    private final IncludedBuildTaskGraph taskGraph;
    private BuildIdentifier build;
    private String taskPath;

    @Inject
    public CompositeBuildTaskDelegate(IncludedBuildTaskGraph taskGraph) {
        this.taskGraph = taskGraph;
    }

    @Input
    public BuildIdentifier getBuild() {
        return build;
    }

    public void setBuild(BuildIdentifier build) {
        this.build = build;
    }

    @Input
    public String getTaskPath() {
        return taskPath;
    }

    public void setTaskPath(String taskPath) {
        this.taskPath = taskPath;
    }

    @Internal
    @Override
    public boolean isComplete() {
        return taskGraph.isComplete(build, taskPath);
    }
}
