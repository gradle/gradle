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
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport.buildFromTask;

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
        List<DefaultGradleProject> children = new ArrayList<DefaultGradleProject>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        DefaultGradleProject gradleProject = new DefaultGradleProject()
                .setProjectIdentifier(new DefaultProjectIdentifier(project.getRootDir(), project.getPath()))
                .setName(project.getName())
                .setDescription(project.getDescription())
                .setBuildDirectory(project.getBuildDir())
                .setProjectDirectory(project.getProjectDir())
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
            return Collections.emptyList();
        } else if ("skip_task_graph_realization".equals(projectOptions)) {
            return tasks.getNames().stream().map(t -> buildFromTaskName(new LaunchableGradleProjectTask(), owner.getProjectIdentifier(), t)).collect(Collectors.toList());
        }

        tasks.discoverTasks();
        tasks.realize();
        SortedSet<String> taskNames = tasks.getNames();
        List<LaunchableGradleTask> out = new ArrayList<LaunchableGradleTask>(taskNames.size());
        for (String taskName : taskNames) {
            Task t = tasks.findByName(taskName);
            if (t != null) {
                out.add(buildFromTask(new LaunchableGradleProjectTask(), owner.getProjectIdentifier(), t).setProject(owner));
            }
        }

        return out;
    }

    public static <T extends LaunchableGradleTask> T buildFromTaskName(T target, DefaultProjectIdentifier projectIdentifier, String taskName) {
        String taskPath = projectIdentifier.getProjectPath() + ":" + taskName;
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
