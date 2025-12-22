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

import org.gradle.api.GradleException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.composite.BuildIncludeListener;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;

@NullMarked
public class GradleBuildBuilder implements BuildScopeModelBuilder {
    public static final String GRADLE_BUILD_MODEL_NAME = "org.gradle.tooling.model.gradle.GradleBuild";

    private final BuildStateRegistry buildStateRegistry;
    private final BuildIncludeListener failedIncludedBuildsRegistry;
    private final FailureFactory failureFactory;

    public GradleBuildBuilder(
        BuildStateRegistry buildStateRegistry,
        BuildIncludeListener failedIncludedBuildsRegistry,
        FailureFactory failureFactory
    ) {
        this.buildStateRegistry = buildStateRegistry;
        this.failedIncludedBuildsRegistry = failedIncludedBuildsRegistry;
        this.failureFactory = failureFactory;
    }

    @Override
    public boolean canBuild(String modelName) {
        return GRADLE_BUILD_MODEL_NAME.equals(modelName);
    }

    @Override
    public ToolingModelBuilderResultInternal create(BuildState target) {
        return new ResilientGradleBuildCreator(target).create();
    }

    @NullMarked
    private class ResilientGradleBuildCreator {
        private final BuildState target;
        private final Map<BuildState, DefaultGradleBuild> all = new LinkedHashMap<>();
        private final Collection<Failure> failures = new LinkedHashSet<>();

        ResilientGradleBuildCreator(BuildState target) {
            this.target = target;
        }

        private ToolingModelBuilderResultInternal create() {
            ensureProjectsLoaded(target);
            DefaultGradleBuild gradleBuild = convert(target);
            List<Failure> allFailures = failures.stream()
                .distinct()
                .collect(toImmutableList());
            return ToolingModelBuilderResultInternal.of(gradleBuild, allFailures);
        }

        private void addIncludedBuilds(GradleInternal gradle, DefaultGradleBuild model) {
            for (IncludedBuildInternal reference : gradle.includedBuilds()) {
                BuildState target = reference.getTarget();
                if (target instanceof IncludedBuildState || target instanceof RootBuildState) {
                    model.addIncludedBuild(convert(target));
                } else {
                    throw new IllegalStateException("Unknown build type: " + reference.getClass().getName());
                }
            }
        }

        private void addAllImportableBuilds(BuildState targetBuild, GradleInternal gradle, DefaultGradleBuild model) {
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

        private void ensureProjectsLoaded(BuildState target) {
            try {
                target.ensureProjectsLoaded();
            } catch (GradleException e) {
                failures.add(failureFactory.create(e));
            }
        }

        private DefaultGradleBuild convert(BuildState targetBuild) {
            DefaultGradleBuild model = all.get(targetBuild);
            if (model != null) {
                return model;
            }
            model = new DefaultGradleBuild();
            all.put(targetBuild, model);

            ensureProjectsLoaded(targetBuild);

            GradleInternal gradle = targetBuild.getMutableModel();
            addProjectsAndBuildIdentifier(targetBuild, model);
            try {
                addFailedBuilds(targetBuild, model);
                addIncludedBuilds(gradle, model);
            } catch (IllegalStateException e) {
                //Ignore, happens when included builds are not accessible, but we need this for resiliency
            }
            addAllImportableBuilds(targetBuild, gradle, model);
            return model;
        }

        private void addProjectsAndBuildIdentifier(BuildState targetBuild, DefaultGradleBuild model) {
            // If projects are loaded, just add them normally
            if (targetBuild.isProjectsLoaded()) {
                addProjects(targetBuild, model);
                return;
            }

            // Else try to find a root project from the settings
            Set<BuildState> brokenBuilds = failedIncludedBuildsRegistry.getBrokenBuilds();
            Set<SettingsInternal> brokenSettings = failedIncludedBuildsRegistry.getBrokenSettings();
            if (!brokenBuilds.contains(targetBuild) && !brokenSettings.isEmpty()) {
                Optional<SettingsInternal> brokenSettingsInternal = findBrokenSettingsForBuild(targetBuild, brokenSettings);
                if (brokenSettingsInternal.isPresent()) {
                    ProjectDescriptor rootProject = brokenSettingsInternal.get().getRootProject();
                    BasicGradleProject root = convertRoot(targetBuild, rootProject);
                    model.setRootProject(root);
                    model.addProject(root);
                }
            }

            // Build identifier is set via a root project,
            // so if a root project is not set, try to set build identifier differently
            if (model.getRootProject() == null
                && targetBuild instanceof IncludedBuildState
                && ((IncludedBuildState) targetBuild).getBuildDefinition().getBuildRootDir() != null) {
                model.setBuildIdentifier(new DefaultBuildIdentifier(((IncludedBuildState) targetBuild).getBuildDefinition().getBuildRootDir()));
            }
        }

        private Optional<SettingsInternal> findBrokenSettingsForBuild(BuildState buildState, Set<SettingsInternal> brokenSettings) {
            File buildRootDir = buildState.getBuildRootDir();
            return brokenSettings.stream()
                .filter(settings -> settings.getRootDir().equals(buildRootDir))
                .findFirst();
        }

        private BasicGradleProject convertRoot(BuildState owner, ProjectDescriptor project) {
            DefaultProjectIdentifier id = new DefaultProjectIdentifier(owner.getBuildRootDir(), project.getPath());
            return new BasicGradleProject()
                .setName(project.getName())
                .setProjectIdentifier(id)
                .setBuildTreePath(project.getPath())
                .setProjectDirectory(project.getProjectDir());
        }

        private void addFailedBuilds(BuildState targetBuild, DefaultGradleBuild model) {
            for (BuildState entry : failedIncludedBuildsRegistry.getBrokenBuilds()) {
                BuildState parent = entry.getParent();
                if (parent != null && parent.equals(targetBuild)) {
                    model.addIncludedBuild(convert(entry));
                }
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
        if (project.getParent() != null) {
            converted.setParent(convertedProjects.get(project.getParent()));
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
        BasicGradleProject convertedRootProject = convert(target, rootProject, convertedProjects);
        model.setRootProject(convertedRootProject);

        for (ProjectState project : target.getProjects().getAllProjects()) {
            model.addProject(convertedProjects.get(project));
        }
    }

}
