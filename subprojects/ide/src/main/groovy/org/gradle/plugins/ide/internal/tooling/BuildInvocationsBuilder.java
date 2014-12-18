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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.tooling.internal.impl.DefaultBuildInvocations;
import org.gradle.tooling.internal.impl.LaunchableGradleTask;
import org.gradle.tooling.internal.impl.LaunchableGradleTaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {

    private static class TaskSelectorBuilder {
        private String selectorName;
        private String description;

        public TaskSelectorBuilder(String selectorName, String description) {
            this.selectorName = selectorName;
            this.description = description;
        }

        public void addDescription(String description) {
            if (description != null) {
                this.description = description;
            }
        }

        LaunchableGradleTaskSelector build(Project project, Set<String> visibleTasks) {
            String desc = description != null
                    ? description
                    : (project.getParent() != null
                        ? String.format("%s:%s task selector", project.getPath(), selectorName)
                        : String.format("%s task selector", selectorName));
            return new LaunchableGradleTaskSelector().
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectPath(project.getPath()).
                    setDescription(desc).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())).
                    setPublic(visibleTasks.contains(selectorName));
        }
    }
    private final ProjectTaskLister taskLister;

    public BuildInvocationsBuilder(ProjectTaskLister taskLister) {
        this.taskLister = taskLister;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        List<LaunchableGradleTaskSelector> selectors = Lists.newArrayList();
        Map<String, TaskSelectorBuilder> aggregatedTasks = Maps.newLinkedHashMap();
        Set<String> visibleTasks = Sets.newLinkedHashSet();
        findTasks(project, aggregatedTasks, visibleTasks);
        for (TaskSelectorBuilder selectorData : aggregatedTasks.values()) {
            selectors.add(selectorData.build(project, visibleTasks));
        }
        return new DefaultBuildInvocations()
                .setSelectors(selectors)
                .setTasks(tasks(project));
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        return buildAll(modelName, implicitProject ? project.getRootProject() : project);
    }

    // build tasks without project reference
    private List<LaunchableGradleTask> tasks(Project project) {
        List<LaunchableGradleTask> tasks = Lists.newArrayList();
        for (Task task : taskLister.listProjectTasks(project)) {
            tasks.add(new LaunchableGradleTask()
                    .setPath(task.getPath())
                    .setName(task.getName())
                    .setDisplayName(task.toString())
                    .setDescription(task.getDescription())
                    .setPublic(task.getGroup() != null));
        }
        return tasks;
    }

    private void findTasks(Project project, Map<String, TaskSelectorBuilder> tasks, Collection<String> visibleTasks) {
        for (Project child : project.getChildProjects().values()) {
            findTasks(child, tasks, visibleTasks);
        }
        for (Task task : taskLister.listProjectTasks(project)) {
            TaskSelectorBuilder selectorBuilder = tasks.get(task.getName());
            if (selectorBuilder != null) {
                selectorBuilder.addDescription(task.getDescription());
            } else {
                tasks.put(task.getName(), new TaskSelectorBuilder(task.getName(), task.getDescription()));
            }
            if (task.getGroup() != null) {
                visibleTasks.add(task.getName());
            }
        }
    }

}
