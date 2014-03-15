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

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublication;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.tooling.internal.gradle.DefaultGradleModuleVersion;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.gradle.DefaultGradlePublication;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Builds the GradleProject that contains the project hierarchy and task information
 */
public class GradleProjectBuilder implements ToolingModelBuilder {
    private final ProjectPublicationRegistry publicationRegistry;

    public GradleProjectBuilder(ProjectPublicationRegistry publicationRegistry) {
        this.publicationRegistry = publicationRegistry;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.GradleProject");
    }

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
                .setPath(project.getPath())
                .setName(project.getName())
                .setDescription(project.getDescription())
                .setChildren(children);

        gradleProject.getBuildScript().setSourceFile(project.getBuildFile());
        gradleProject.setTasks(tasks(gradleProject, project.getTasks()));
        gradleProject.setPublications(publications(project.getPath()));

        for (DefaultGradleProject child : children) {
            child.setParent(gradleProject);
        }

        return gradleProject;
    }

    private static List<DefaultGradleTask> tasks(DefaultGradleProject owner, TaskContainer tasks) {
        List<DefaultGradleTask> out = new LinkedList<DefaultGradleTask>();

        for (Task t : tasks) {
            out.add(new DefaultGradleTask()
                    .setPath(t.getPath())
                    .setName(t.getName())
                    .setDisplayName(t.getName() + " task (" + t.getPath() + ")")
                    .setDescription(t.getDescription())
                    .setProject(owner));
        }

        return out;
    }

    private List<DefaultGradlePublication> publications(String projectPath) {
        List<DefaultGradlePublication> gradlePublications = Lists.newArrayList();

        Set<ProjectPublication> projectPublications = publicationRegistry.getPublications(projectPath);
        for (ProjectPublication projectPublication : projectPublications) {
            gradlePublications.add(new DefaultGradlePublication()
                    .setId(new DefaultGradleModuleVersion(projectPublication.getId())));
        }

        return gradlePublications;
    }
}
