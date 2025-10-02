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

import com.google.common.collect.Streams;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.composite.BuildIncludeListener;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.plugins.ide.internal.tooling.model.DefaultResilientGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.gradle.plugins.ide.internal.tooling.GradleBuildBuilder.addProjects;

@NullMarked
public class ResilientGradleBuildBuilder implements BuildScopeModelBuilder {
    private final BuildStateRegistry buildStateRegistry;
    private final BuildIncludeListener failedIncludedBuildsRegistry;

    public ResilientGradleBuildBuilder(
        BuildStateRegistry buildStateRegistry,
        BuildIncludeListener failedIncludedBuildsRegistry
    ) {
        this.buildStateRegistry = buildStateRegistry;
        this.failedIncludedBuildsRegistry = failedIncludedBuildsRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.ResilientGradleBuild");
    }


    @Override
    public DefaultResilientGradleBuild create(BuildState target) {
        return new ResilientGradleBuildCreator(target).create();
    }

    @NullMarked
    private class ResilientGradleBuildCreator {
        private final Map<BuildState, Failure> brokenBuilds = new HashMap<>();
        private final Map<SettingsInternal, Failure> brokenSettings = new HashMap<>();
        private final BuildState target;
        private final Map<BuildState, DefaultGradleBuild> all = new LinkedHashMap<>();

        ResilientGradleBuildCreator(BuildState target) {
            this.target = target;
        }

        DefaultResilientGradleBuild create() {
            ensureProjectsLoaded(target);
            return new DefaultResilientGradleBuild(convert(target),
                Streams.concat(brokenBuilds.values().stream(), brokenSettings.values().stream())
                    .map(Object::toString)
                    .collect(toImmutableList()));
        }

        protected void addIncludedBuilds(GradleInternal gradle, DefaultGradleBuild model) {
            for (IncludedBuildInternal reference : gradle.includedBuilds()) {
                BuildState target = reference.getTarget();
                if (target instanceof IncludedBuildState || target instanceof RootBuildState) {
                    model.addIncludedBuild(convert(target));
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

        protected void ensureProjectsLoaded(BuildState target) {
            try {
                target.ensureProjectsLoaded();
            } catch (GradleException e) {
                if (e.getCause() instanceof org.gradle.kotlin.dsl.support.ScriptCompilationException) {
                    brokenBuilds.putAll(failedIncludedBuildsRegistry.getBrokenBuilds());
                    brokenSettings.putAll(failedIncludedBuildsRegistry.getBrokenSettings());
                    return;
                }
                throw e;
            }
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

            Failure failure = brokenBuilds.get(targetBuild);
            if (failure == null && !brokenSettings.isEmpty()) {
                Map.Entry<SettingsInternal, Failure> settingsEntry = brokenSettings.entrySet().iterator().next();
                ProjectDescriptor rootProject = settingsEntry.getKey().getRootProject();
                BasicGradleProject root = convertRoot(targetBuild, rootProject);
                model.setRootProject(root);
                model.addProject(root);
            }

            GradleInternal gradle = targetBuild.getMutableModel();
            if (targetBuild.isProjectsLoaded()) {
                addProjects(targetBuild, model);
            }
            try {
                addFailedBuilds(targetBuild, model);
                addIncludedBuilds(gradle, model);
            } catch (IllegalStateException e) {
                //ignore, happens when included builds are not accessible, but we need this for resiliency
            }
            addAllImportableBuilds(targetBuild, gradle, model);
            return model;
        }

        protected BasicGradleProject convertRoot(BuildState owner, ProjectDescriptor project) {
            DefaultProjectIdentifier id = new DefaultProjectIdentifier(owner.getBuildRootDir(), project.getPath());
            return new BasicGradleProject()
                .setName(project.getName())
                .setProjectIdentifier(id)
                .setBuildTreePath(project.getPath())
                .setProjectDirectory(project.getProjectDir());
        }

        private void addFailedBuilds(BuildState targetBuild, DefaultGradleBuild model) {
            for (Map.Entry<BuildState, Failure> entry : brokenBuilds.entrySet()) {
                BuildState parent = entry.getKey().getParent();
                if (parent != null && parent.equals(targetBuild)) {
                    model.addIncludedBuild(convert(entry.getKey()));
                }
            }
        }

    }
}
