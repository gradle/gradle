/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;
import static org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport.buildFromTask;
import static org.gradle.util.Path.SEPARATOR;

/**
 * Builds the GradleProject that contains the project hierarchy and task information
 */
public class GradleProjectBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.GradleProject");
    }

    /**
     * Builds a hierarchical model of the root project, regardless of the target project parameter.
     */
    @Override
    public Object buildAll(String modelName, Project project) {
        return buildAll(project);
    }

    public DefaultGradleProject buildAll(Project project) {
        boolean realizeTasks = GradleProjectBuilderOptions.shouldRealizeTasks();
        return buildHierarchy(project.getRootProject(), realizeTasks);
    }

    /**
     * When {@code realizeTasks} is false, the project's task graph will not be realized, and the task list in the model will be empty
     */
    private static DefaultGradleProject buildHierarchy(Project project, boolean realizeTasks) {
        List<DefaultGradleProject> children = getChildProjectsForInternalUse(project).stream()
            .map(it -> buildHierarchy(it, realizeTasks))
            .collect(toList());

        String projectIdentityPath = ((ProjectInternal) project).getIdentityPath().getPath();
        DefaultGradleProject gradleProject = new DefaultGradleProject()
            .setProjectIdentifier(new DefaultProjectIdentifier(project.getRootDir(), project.getPath()))
            .setName(project.getName())
            .setDescription(project.getDescription())
            .setBuildDirectory(project.getLayout().getBuildDirectory().getAsFile().get())
            .setProjectDirectory(project.getProjectDir())
            .setBuildTreePath(projectIdentityPath)
            .setChildren(children);

        gradleProject.getBuildScript().setSourceFile(project.getBuildFile());

        for (DefaultGradleProject child : children) {
            child.setParent(gradleProject);
        }

        if (realizeTasks) {
            List<LaunchableGradleProjectTask> tasks = collectTasks(gradleProject, (TaskContainerInternal) project.getTasks());
            gradleProject.setTasks(tasks);
        }

        return gradleProject;
    }

    private static List<LaunchableGradleProjectTask> collectTasks(DefaultGradleProject owner, TaskContainerInternal tasks) {
        tasks.discoverTasks();
        tasks.realize();

        return tasks.getNames().stream()
            .map(tasks::findByName)
            .filter(Objects::nonNull)
            .map(task -> buildTaskModel(owner, task)).collect(toList());
    }

    private static LaunchableGradleProjectTask buildTaskModel(DefaultGradleProject owner, Task task) {
        LaunchableGradleProjectTask model = buildFromTask(new LaunchableGradleProjectTask(), owner.getProjectIdentifier(), task);
        model.setProject(owner)
            .setBuildTreePath(getBuildTreePath(owner, task));
        return model;
    }

    private static String getBuildTreePath(DefaultGradleProject owner, Task task) {
        String ownerBuildTreePath = owner.getBuildTreePath();
        String buildTreePath = SEPARATOR + task.getName();
        if (SEPARATOR.equals(ownerBuildTreePath)) {
            return buildTreePath;
        }
        return ownerBuildTreePath + buildTreePath;
    }

}
