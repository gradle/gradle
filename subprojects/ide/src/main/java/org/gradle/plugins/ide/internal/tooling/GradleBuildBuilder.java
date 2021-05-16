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
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.composite.IncludedRootBuild;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradleBuildBuilder implements ToolingModelBuilder {
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
        return convert(targetBuild, new LinkedHashMap<>());
    }

    private DefaultGradleBuild convert(BuildState targetBuild, Map<BuildState, DefaultGradleBuild> all) {
        DefaultGradleBuild model = all.get(targetBuild);
        if (model != null) {
            return model;
        }
        model = new DefaultGradleBuild();
        all.put(targetBuild, model);

        GradleInternal gradle = targetBuild.getMutableModel();
        addProjects(gradle, model);
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
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            if (includedBuild instanceof IncludedBuildState) {
                IncludedBuildState includedBuildState = (IncludedBuildState) includedBuild;
                includedBuildState.getConfiguredBuild();
                DefaultGradleBuild convertedIncludedBuild = convert(includedBuildState, all);
                model.addIncludedBuild(convertedIncludedBuild);
            } else if (includedBuild instanceof IncludedRootBuild) {
                DefaultGradleBuild rootBuild = convert(buildStateRegistry.getRootBuild(), all);
                model.addIncludedBuild(rootBuild);
            } else {
                throw new IllegalStateException("Unknown build type: " + includedBuild.getClass().getName());
            }
        }
    }

    private void addProjects(GradleInternal gradle, DefaultGradleBuild model) {
        Map<Project, BasicGradleProject> convertedProjects = new LinkedHashMap<>();

        Project rootProject = gradle.getRootProject();
        BasicGradleProject convertedRootProject = convert(rootProject, convertedProjects);
        model.setRootProject(convertedRootProject);

        for (Project project : rootProject.getAllprojects()) {
            model.addProject(convertedProjects.get(project));
        }
    }

    private BasicGradleProject convert(Project project, Map<Project, BasicGradleProject> convertedProjects) {
        DefaultProjectIdentifier id = new DefaultProjectIdentifier(project.getRootDir(), project.getPath());
        BasicGradleProject converted = new BasicGradleProject().setName(project.getName()).setProjectIdentifier(id);
        converted.setProjectDirectory(project.getProjectDir());
        if (project.getParent() != null) {
            converted.setParent(convertedProjects.get(project.getParent()));
        }
        convertedProjects.put(project, converted);
        for (Project child : project.getChildProjects().values()) {
            converted.addChild(convert(child, convertedProjects));
        }
        return converted;
    }
}
