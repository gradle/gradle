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
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradleBuildBuilder implements BuildScopeModelBuilder {
    private final BuildStateRegistry buildStateRegistry;

    public GradleBuildBuilder(BuildStateRegistry buildStateRegistry) {
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.GradleBuild");
    }

    @Override
    public DefaultGradleBuild create(BuildState target) {
        return new GradleBuildCreator(target).create();
    }

    @NullMarked
    protected class GradleBuildCreator {
        private final BuildState target;
        private final Map<BuildState, DefaultGradleBuild> all = new LinkedHashMap<>();

        GradleBuildCreator(BuildState target) {
            this.target = target;
        }

        DefaultGradleBuild create() {
            ensureProjectsLoaded(target);
            return convert(target);
        }

        protected void ensureProjectsLoaded(BuildState target) {
            target.ensureProjectsLoaded();
        }

        protected DefaultGradleBuild convert(BuildState targetBuild) {
            DefaultGradleBuild model = all.get(targetBuild);
            if (model != null) {
                return model;
            }
            model = new DefaultGradleBuild();
            all.put(targetBuild, model);

            // Make sure the project tree has been loaded and can be queried (but not necessarily configured)
            ensureProjectsLoaded(targetBuild);

            GradleInternal gradle = targetBuild.getMutableModel();
            addProjects(targetBuild, model);
            addIncludedBuilds(gradle, model);
            addAllImportableBuilds(targetBuild, gradle, model);

            return model;
        }

        protected void addIncludedBuilds(GradleInternal gradle, DefaultGradleBuild model) {
            for (IncludedBuildInternal reference : gradle.includedBuilds()) {
                BuildState target = reference.getTarget();
                if (target instanceof IncludedBuildState || target instanceof RootBuildState) {
                    DefaultGradleBuild convertedIncludedBuild = convert(target);
                    model.addIncludedBuild(convertedIncludedBuild);
                } else {
                    throw new IllegalStateException("Unknown build type: " + reference.getClass().getName());
                }
            }
        }

        protected void addAllImportableBuilds(BuildState targetBuild, GradleInternal gradle, DefaultGradleBuild model) {
            if (gradle.getParent() == null) {
                List<DefaultGradleBuild> allBuilds = new ArrayList<>();
                buildStateRegistry.visitBuilds(buildState -> {
                    // Do not include the root build and only include builds that are intended to be imported into an IDE
                    if (buildState != targetBuild && buildState.isImportableBuild()) {
                        allBuilds.add(convert(buildState));
                    }
                });
                model.addBuilds(allBuilds);
            }
        }

    }

    static protected BasicGradleProject convert(BuildState owner, ProjectState project, Map<ProjectState, BasicGradleProject> convertedProjects) {
        DefaultProjectIdentifier id = new DefaultProjectIdentifier(owner.getBuildRootDir(), project.getProjectPath().asString());
        BasicGradleProject converted = new BasicGradleProject()
            .setName(project.getName())
            .setProjectIdentifier(id)
            .setBuildTreePath(project.getIdentityPath().asString())
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

    static protected void addProjects(BuildState target, DefaultGradleBuild model) {
        Map<ProjectState, BasicGradleProject> convertedProjects = new LinkedHashMap<>();

        ProjectState rootProject = target.getProjects().getRootProject();
        BasicGradleProject convertedRootProject = GradleBuildBuilder.convert(target, rootProject, convertedProjects);
        model.setRootProject(convertedRootProject);

        for (ProjectState project : target.getProjects().getAllProjects()) {
            model.addProject(convertedProjects.get(project));
        }
    }

}
