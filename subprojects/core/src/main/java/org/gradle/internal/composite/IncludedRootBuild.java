/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.composite;

import org.gradle.api.internal.tasks.DefaultTaskReference;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;

import java.io.File;

public class IncludedRootBuild implements IncludedBuildInternal {

    private final CompositeBuildParticipantBuildState rootBuild;
    private final TaskDependencyFactory taskDependencyFactory;

    public IncludedRootBuild(CompositeBuildParticipantBuildState rootBuild, TaskDependencyFactory taskDependencyFactory) {
        this.rootBuild = rootBuild;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    public CompositeBuildParticipantBuildState getRootBuild() {
        return rootBuild;
    }

    @Override
    public String getName() {
        return rootBuild.getProjects().getRootProject().getName();
    }

    @Override
    public File getProjectDir() {
        return rootBuild.getBuildRootDir();
    }

    @Override
    public TaskReference task(String pathStr) {
        return DefaultTaskReference.create(pathStr, taskDependencyFactory);
    }

    @Override
    public BuildState getTarget() {
        return rootBuild;
    }

}
