/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultIsolatedGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleProjectTask;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.gradle.api.internal.project.ProjectHierarchyUtils.getChildProjectsForInternalUse;

/**
 * Builds the {@link GradleProject} model that contains the project hierarchy and task information.
 */
@NonNullApi
public class IsolatedProjectsSafeGradleProjectBuilder implements ToolingModelBuilder, GradleProjectBuilderInternal {

    private final IntermediateToolingModelProvider intermediateToolingModelProvider;

    public IsolatedProjectsSafeGradleProjectBuilder(IntermediateToolingModelProvider intermediateToolingModelProvider) {
        this.intermediateToolingModelProvider = intermediateToolingModelProvider;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.GradleProject");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        return buildRoot(project);
    }

    @Override
    public DefaultGradleProject buildRoot(Project project) {
        ProjectInternal rootProject = (ProjectInternal) project.getRootProject();
        // We must request isolated root model instead of building it directly,
        // because the original target of the model may not have been a root project
        DefaultIsolatedGradleProject rootIsolatedModel = mapToIsolatedModels(singletonList(rootProject)).get(0);
        return build(rootIsolatedModel, rootProject);
    }

    private DefaultGradleProject build(DefaultIsolatedGradleProject isolatedModel, ProjectInternal project) {
        DefaultGradleProject model = buildWithoutChildren(project, isolatedModel);
        Collection<Project> childProjects = getChildProjectsForInternalUse(project);
        List<DefaultIsolatedGradleProject> childIsolatedModels = mapToIsolatedModels(childProjects);

        List<DefaultGradleProject> childModels = new ArrayList<>();
        int i = 0;
        for (Project childProject : childProjects) {
            DefaultIsolatedGradleProject childIsolatedModel = childIsolatedModels.get(i++);
            DefaultGradleProject childModel = build(childIsolatedModel, (ProjectInternal) childProject);
            childModel.setParent(model);
            childModels.add(childModel);
        }
        model.setChildren(childModels);
        return model;
    }

    private List<DefaultIsolatedGradleProject> mapToIsolatedModels(Collection<Project> childProjects) {
        return intermediateToolingModelProvider
            .getModels(new ArrayList<>(childProjects), "org.gradle.tooling.model.internal.gradle.IsolatedGradleProject", DefaultIsolatedGradleProject.class);
    }

    private static DefaultGradleProject buildWithoutChildren(ProjectInternal project, DefaultIsolatedGradleProject isolatedModel) {
        DefaultGradleProject model = new DefaultGradleProject();

        model.setProjectIdentifier(isolatedModel.getProjectIdentifier())
            .setName(isolatedModel.getName())
            .setDescription(isolatedModel.getDescription())
            .setBuildDirectory(isolatedModel.getBuildDirectory())
            .setProjectDirectory(isolatedModel.getProjectDirectory())
            .setBuildTreePath(project.getIdentityPath().getPath());

        model.getBuildScript().setSourceFile(isolatedModel.getBuildScript().getSourceFile());

        Collection<LaunchableGradleTask> isolatedTasks = isolatedModel.getTasks();
        if (!isolatedTasks.isEmpty()) {
            model.setTasks(isolatedTasks.stream().map(it -> buildProjectTask(model, it)).collect(toList()));
        }

        return model;
    }

    public static LaunchableGradleProjectTask buildProjectTask(DefaultGradleProject owner, LaunchableGradleTask model) {
        LaunchableGradleProjectTask target = new LaunchableGradleProjectTask();
        target.setPath(model.getPath())
            .setName(model.getName())
            .setGroup(model.getGroup())
            .setDisplayName(model.getDisplayName())
            .setDescription(model.getDescription())
            .setPublic(model.isPublic())
            .setProjectIdentifier(model.getProjectIdentifier())
            .setBuildTreePath(model.getBuildTreePath());
        target.setProject(owner);
        return target;
    }

}
