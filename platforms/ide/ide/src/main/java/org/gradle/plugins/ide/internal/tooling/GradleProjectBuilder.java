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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
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

    @Override
    public Object buildAll(String modelName, Project project) {
        return buildHierarchy(project.getRootProject());
    }

    public DefaultGradleProject buildAll(Project project) {
        return buildHierarchy(project.getRootProject());
    }

    private DefaultGradleProject buildHierarchy(Project project) {
        List<DefaultGradleProject> children = getChildProjectsForInternalUse(project).stream()
            .map(this::buildHierarchy)
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

        /*
            Internal system property to investigate model loading performance in IDEA/Android Studio.
            The model loading can be altered with the following values:
              - "omit_all_tasks": The model builder won't realize the task graph. The returned model will contain an empty task list.
              - "skip_task_graph_realization":  The model builder won't realize the task graph. The returned model will contain artificial tasks created from the task names.
              - "skip_task_serialization":  The model builder will realize the task graph but won't send it to the client.
              - "unmodified" (or any other value): The model builder will run unchanged.
         */
        String projectOptions = System.getProperty("org.gradle.internal.GradleProjectBuilderOptions", "unmodified");
        List<LaunchableGradleTask> tasks = tasks(gradleProject, (TaskContainerInternal) project.getTasks(), projectOptions);

        if (!"skip_task_serialization".equals(projectOptions)) {
            gradleProject.setTasks(tasks);
        }

        for (DefaultGradleProject child : children) {
            child.setParent(gradleProject);
        }

        return gradleProject;
    }

    private static List<LaunchableGradleTask> tasks(DefaultGradleProject owner, TaskContainerInternal tasks, String projectOptions) {
        if ("omit_all_tasks".equals(projectOptions)) {
            return ImmutableList.of();
        }
        if ("skip_task_graph_realization".equals(projectOptions)) {
            return tasks.getNames().stream()
                .map(taskName -> buildFromTaskName(new LaunchableGradleProjectTask(), owner.getProjectIdentifier(), taskName)
                    .setBuildTreePath(owner.getBuildTreePath() + SEPARATOR + taskName))
                .collect(toList());
        }

        tasks.discoverTasks();
        tasks.realize();

        return tasks.getNames().stream()
            .map(tasks::findByName)
            .filter(Objects::nonNull)
            .map(task -> buildFromTask(new LaunchableGradleProjectTask(), owner.getProjectIdentifier(), task)
                .setProject(owner)
                .setBuildTreePath(getBuildTreePath(owner, task))).collect(toList());
    }

    private static String getBuildTreePath(DefaultGradleProject owner, Task task) {
        String ownerBuildTreePath = owner.getBuildTreePath();
        String buildTreePath = SEPARATOR + task.getName();
        if (SEPARATOR.equals(ownerBuildTreePath)) {
            return buildTreePath;
        }
        return ownerBuildTreePath + buildTreePath;
    }

    public static <T extends LaunchableGradleTask> T buildFromTaskName(T target, DefaultProjectIdentifier projectIdentifier, String taskName) {
        String taskPath = projectIdentifier.getProjectPath() + SEPARATOR + taskName;
        target.setPath(taskPath)
            .setName(taskName)
            .setGroup("undefined")
            .setDisplayName(taskPath)
            .setDescription("")
            .setPublic(true)
            .setProjectIdentifier(projectIdentifier);

        return target;
    }
}
