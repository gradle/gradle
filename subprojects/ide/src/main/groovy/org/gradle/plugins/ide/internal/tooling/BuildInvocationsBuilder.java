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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.internal.gradle.PartialGradleProject;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    public BuildInvocationsBuilder(GradleProjectBuilder gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        Multimap<String, String> aggregatedTasks = findTasks(project);
        for (String selectorName : aggregatedTasks.keySet()) {
            selectors.add(new DefaultGradleTaskSelector().
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectDir(project.getProjectDir()).
                    setProjectPath(project.getPath()).
                    setDescription(project.getParent() != null
                            ? String.format("%s:%s task selector", project.getPath(), selectorName)
                            : String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())));
        }
        return new DefaultBuildInvocations()
                .setSelectors(selectors)
                .setTasks(convertTasks(new ArrayList(gradleProjectBuilder.buildAll(project).findByPath(project.getPath()).getTasks())));
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        if (implicitProject) {
            DefaultGradleProject gradleProject = gradleProjectBuilder.buildAll(project);
            List<DefaultGradleTask> tasks = new ArrayList<DefaultGradleTask>();
            fillTaskList(gradleProject, tasks);
            return new DefaultBuildInvocations()
                    .setSelectors(buildRecursively(modelName, project.getRootProject()))
                    .setTasks(convertTasks(tasks));
        } else {
            return buildAll(modelName, project);
        }
    }

    private void fillTaskList(PartialGradleProject gradleProject, List<DefaultGradleTask> tasks) {
        tasks.addAll(gradleProject.getTasks());
        for (PartialGradleProject childProject : gradleProject.getChildren()) {
            fillTaskList(childProject, tasks);
        }
    }

    private List<DefaultGradleTaskSelector> buildRecursively(String modelName, Project project) {
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        selectors.addAll(buildAll(modelName, project).getTaskSelectors());
        for (Project childProject : project.getSubprojects()) {
            selectors.addAll(buildRecursively(modelName, childProject));
        }
        return selectors;
    }

    private List<DefaultGradleTask> convertTasks(List<DefaultGradleTask> tasks) {
        for (DefaultGradleTask task :  tasks) {
            task.setProject(null);
        }
        return tasks;
    }

    private Multimap<String, String> findTasks(Project project) {
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();
        for (Project child : project.getSubprojects()) {
            Multimap<String, String> childTasks = findTasks(child);
            aggregatedTasks.putAll(childTasks);
        }
        for (Task task : project.getTasks()) {
            aggregatedTasks.put(task.getName(), task.getPath());
        }
        return aggregatedTasks;
    }

}
