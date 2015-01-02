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

import com.google.common.collect.*;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.tooling.internal.consumer.converters.TaskNameComparator;
import org.gradle.tooling.internal.impl.DefaultBuildInvocations;
import org.gradle.tooling.internal.impl.LaunchableGradleTask;
import org.gradle.tooling.internal.impl.LaunchableGradleTaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {
    @SuppressWarnings("RedundantStringConstructorCall")
    private static final String NULL_STRING = new String(); // ensure unique instance to use it as a null-string placeholder

    private final ProjectTaskLister taskLister;

    public BuildInvocationsBuilder(ProjectTaskLister taskLister) {
        this.taskLister = taskLister;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    @SuppressWarnings("StringEquality")
    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }

        // construct task selectors
        List<LaunchableGradleTaskSelector> selectors = Lists.newArrayList();
        TreeBasedTable<String, String, String> aggregatedTasksWithDescription = TreeBasedTable.create(Ordering.usingToString(), new TaskNameComparator());
        Set<String> visibleTasks = Sets.newLinkedHashSet();
        findTasks(project, aggregatedTasksWithDescription, visibleTasks);
        for (String selectorName : aggregatedTasksWithDescription.rowKeySet()) {
            SortedMap<String, String> descriptionsFromAllPaths = aggregatedTasksWithDescription.row(selectorName);
            String description = descriptionsFromAllPaths.get(descriptionsFromAllPaths.firstKey());
            selectors.add(new LaunchableGradleTaskSelector().
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectPath(project.getPath()).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())).
                    setDescription(description != NULL_STRING ? description : null).
                    setPublic(visibleTasks.contains(selectorName)));
        }

        // construct project tasks
        List<LaunchableGradleTask> projectTasks = tasks(project);

        // construct build invocations from task selectors and project tasks
        return new DefaultBuildInvocations().setSelectors(selectors).setTasks(projectTasks);
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

    private void findTasks(Project project, Table<String, String, String> tasksWithDescription, Collection<String> visibleTasks) {
        for (Project child : project.getChildProjects().values()) {
            findTasks(child, tasksWithDescription, visibleTasks);
        }

        for (Task task : taskLister.listProjectTasks(project)) {
            // store the description first by task name and then by path
            // this allows to later fish out the description of the task whose name matches the selector name and
            // whose path is the smallest for the given task name (the first entry of the table column)
            // store null description as empty string to avoid that Guava chokes
            tasksWithDescription.put(task.getName(), task.getPath(), task.getDescription() != null ? task.getDescription() : NULL_STRING);

            // visible tasks are specified as those that have a non-empty group
            if (task.getGroup() != null) {
                visibleTasks.add(task.getName());
            }
        }
    }

}
