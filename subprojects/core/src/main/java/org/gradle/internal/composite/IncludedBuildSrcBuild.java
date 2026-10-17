/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.internal.build.StandAloneNestedBuild;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Public reference to a {@code buildSrc} build, exposing it through the same
 * {@link org.gradle.api.initialization.IncludedBuild} API used for regular included builds
 * so that consumers can address tasks via
 * {@code gradle.includedBuild("buildSrc").task(":someTask")}.
 *
 * <p>Reachable only from the {@code Gradle} instance whose owner directly contains the
 * {@code buildSrc} directory.</p>
 */
@NullMarked
public class IncludedBuildSrcBuild implements IncludedBuildInternal {

    private final StandAloneNestedBuild buildSrcBuild;
    private final TaskDependencyFactory taskDependencyFactory;

    public IncludedBuildSrcBuild(StandAloneNestedBuild buildSrcBuild, TaskDependencyFactory taskDependencyFactory) {
        this.buildSrcBuild = buildSrcBuild;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public String getName() {
        return buildSrcBuild.getProjects().getRootProject().getName();
    }

    @Override
    public File getProjectDir() {
        return buildSrcBuild.getBuildRootDir();
    }

    @Override
    public TaskReference task(String pathStr) {
        return DefaultTaskReference.create(pathStr, taskDependencyFactory);
    }

    @Override
    public BuildState getTarget() {
        return buildSrcBuild;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IncludedBuildSrcBuild that = (IncludedBuildSrcBuild) o;
        return buildSrcBuild.equals(that.buildSrcBuild);
    }

    @Override
    public int hashCode() {
        return buildSrcBuild.hashCode();
    }
}
