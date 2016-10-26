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
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

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

    private DefaultGradleProject<LaunchableGradleTask> buildHierarchy(Project project) {
        List<DefaultGradleProject<LaunchableGradleTask>> children = new ArrayList<DefaultGradleProject<LaunchableGradleTask>>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        DefaultGradleProject<LaunchableGradleTask> gradleProject = new DefaultGradleProject<LaunchableGradleTask>()
                .setProjectIdentifier(new DefaultProjectIdentifier(project.getRootDir(), project.getPath()))
                .setName(project.getName())
                .setDescription(project.getDescription())
                .setBuildDirectory(project.getBuildDir())
                .setProjectDirectory(project.getProjectDir())
                .setChildren(children);

        gradleProject.getBuildScript().setSourceFile(project.getBuildFile());
        gradleProject.setTasks(tasks(gradleProject, (TaskContainerInternal) project.getTasks()));

        for (DefaultGradleProject child : children) {
            child.setParent(gradleProject);
        }

        return gradleProject;
    }

    private static List<LaunchableGradleTask> tasks(DefaultGradleProject owner, TaskContainerInternal tasks) {
        tasks.discoverTasks();
        SortedSet<String> taskNames = tasks.getNames();
        List<LaunchableGradleTask> out = new ArrayList<LaunchableGradleTask>(taskNames.size());
        for (String taskName : taskNames) {
            Task t = tasks.findByName(taskName);
            if (t != null) {
                out.add(buildFromTask(new LaunchableGradleProjectTask(), t).setProject(owner).setProjectIdentifier(owner.getProjectIdentifier()));
            }
        }

        return out;
    }
}
