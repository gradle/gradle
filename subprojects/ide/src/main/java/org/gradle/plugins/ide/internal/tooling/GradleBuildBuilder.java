/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradleBuildBuilder implements ToolingModelBuilder, BuildScopeModelBuilder {
    private final BuildStateRegistry buildStateRegistry;

    public GradleBuildBuilder(BuildStateRegistry buildStateRegistry) {
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.GradleBuild");
    }

    @Override
    public DefaultGradleBuild buildAll(String modelName, Project target) {
        BuildState targetBuild = ((GradleInternal) target.getGradle()).getOwner();
        return create(targetBuild);
    }

    @Override
    public DefaultGradleBuild create(BuildState target) {
        target.ensureProjectsLoaded();
        return convert(target, new LinkedHashMap<>());
    }

    private DefaultGradleBuild convert(BuildState targetBuild, Map<BuildState, DefaultGradleBuild> all) {
        DefaultGradleBuild model = all.get(targetBuild);
        if (model != null) {
            return model;
        }
        model = new DefaultGradleBuild();
        all.put(targetBuild, model);

        // Make sure the project tree has been loaded and can be queried (but not necessarily configured)
        targetBuild.ensureProjectsLoaded();

        GradleInternal gradle = targetBuild.getMutableModel();
        addProjects(targetBuild, model);
        addIncludedBuilds(gradle, model, all);

        if (gradle.getParent() == null) {
            List<DefaultGradleBuild> allBuilds = new ArrayList<>();
            buildStateRegistry.visitBuilds(buildState -> {
                // Do not include the root build and only include builds that are intended to be imported into an IDE
                if (buildState != targetBuild && buildState.isImportableBuild()) {
                    allBuilds.add(convert(buildState, all));
                }
            });
            model.addBuilds(allBuilds);
        }

        return model;
    }

    private void addIncludedBuilds(GradleInternal gradle, DefaultGradleBuild model, Map<BuildState, DefaultGradleBuild> all) {
        for (IncludedBuildInternal reference : gradle.includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                IncludedBuildState includedBuildState = (IncludedBuildState) target;
                DefaultGradleBuild convertedIncludedBuild = convert(includedBuildState, all);
                model.addIncludedBuild(convertedIncludedBuild);
            } else if (target instanceof RootBuildState) {
                DefaultGradleBuild rootBuild = convert(target, all);
                model.addIncludedBuild(rootBuild);
            } else {
                throw new IllegalStateException("Unknown build type: " + reference.getClass().getName());
            }
        }
    }

    private static void addProjects(BuildState target, DefaultGradleBuild model) {
        Map<ProjectState, BasicGradleProject> convertedProjects = new LinkedHashMap<>();

        ProjectState rootProject = target.getProjects().getRootProject();
        BasicGradleProject convertedRootProject = convert(target, rootProject, convertedProjects);
        model.setRootProject(convertedRootProject);

        for (ProjectState project : target.getProjects().getAllProjects()) {
            model.addProject(convertedProjects.get(project));
        }
    }

    private static BasicGradleProject convert(BuildState owner, ProjectState project, Map<ProjectState, BasicGradleProject> convertedProjects) {
        DefaultProjectIdentifier id = new DefaultProjectIdentifier(owner.getBuildRootDir(), project.getProjectPath().getPath());
        BasicGradleProject converted = new BasicGradleProject()
            .setName(project.getName())
            .setProjectIdentifier(id)
            .setBuildTreePath(project.getIdentityPath().getPath())
            .setProjectDirectory(project.getProjectDir());
        if (project.getBuildParent() != null) {
            converted.setParent(convertedProjects.get(project.getBuildParent()));
        }
        convertedProjects.put(project, converted);
        for (ProjectState child : project.getChildProjects()) {
            converted.addChild(convert(owner, child, convertedProjects));
        }
        return converted;
    }
}
