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
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.IncludedBuilds;

import java.util.Collections;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class CompositeBuildTaskDelegate extends DefaultTask {
    private String build;
    private String task;

    @Input
    public String getBuild() {
        return build;
    }

    public void setBuild(String build) {
        this.build = build;
    }

    @Input
    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    @TaskAction
    public void executeTaskInOtherBuild() {
        IncludedBuilds includedBuilds = getServices().get(IncludedBuilds.class);
        IncludedBuildExecuter builder = getServices().get(IncludedBuildExecuter.class);
        IncludedBuild includedBuild = includedBuilds.getBuild(build);
        ProjectComponentIdentifier id = newProjectId(includedBuild, ":");
        builder.execute(id, Collections.singleton(task));
    }
}
