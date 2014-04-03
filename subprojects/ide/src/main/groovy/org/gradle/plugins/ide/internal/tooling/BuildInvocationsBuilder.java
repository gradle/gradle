/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.impl.LaunchableGradleTask;
import org.gradle.tooling.internal.impl.LaunchableGradleTaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    public BuildInvocationsBuilder(GradleProjectBuilder gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations<LaunchableGradleTask> buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        List<LaunchableGradleTaskSelector> selectors = Lists.newArrayList();
        Set<String> aggregatedTasks = Sets.newHashSet();
        findTasks(project, aggregatedTasks);
        for (String selectorName : aggregatedTasks) {
            selectors.add(new LaunchableGradleTaskSelector().
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectPath(project.getPath()).
                    setDescription(project.getParent() != null
                            ? String.format("%s:%s task selector", project.getPath(), selectorName)
                            : String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())));
        }
        return new DefaultBuildInvocations<LaunchableGradleTask>()
                .setSelectors(selectors)
                .setTasks(convertTasks(gradleProjectBuilder.buildAll(project).findByPath(project.getPath()).getTasks()));
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        return buildAll(modelName, implicitProject ? project.getRootProject() : project);
    }

    // build tasks without project reference
    private List<LaunchableGradleTask> convertTasks(Iterable<LaunchableGradleTask> tasks) {
        List<LaunchableGradleTask> convertedTasks = Lists.newArrayList();
        for (LaunchableGradleTask task : tasks) {
            convertedTasks.add(new LaunchableGradleTask()
                    .setPath(task.getPath())
                    .setName(task.getName())
                    .setDisplayName(task.toString())
                    .setDescription(task.getDescription()));
        }
        return convertedTasks;
    }

    private void findTasks(Project project, Collection<String> tasks) {
        for (Project child : project.getSubprojects()) {
            findTasks(child, tasks);
        }
        for (Task task : project.getTasks()) {
            tasks.add(task.getName());
        }
    }

}
