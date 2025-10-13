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
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.composite.BuildIncludeListener;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.gradle.plugins.ide.internal.tooling.model.DefaultResilientGradleBuild;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public class ResilientGradleBuildBuilder extends GradleBuildBuilder {
    private final BuildIncludeListener failedIncludedBuildsRegistry;
    private Map<BuildState, Failure> brokenBuilds = new HashMap<>();
    private Map<SettingsInternal, Failure> brokenSettings = new HashMap<>();

    public ResilientGradleBuildBuilder(
        BuildStateRegistry buildStateRegistry,
        BuildIncludeListener failedIncludedBuildsRegistry
    ) {
        super(buildStateRegistry);
        this.failedIncludedBuildsRegistry = failedIncludedBuildsRegistry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.ResilientGradleBuild");
    }

    @Override
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

    @Override
    protected DefaultGradleBuild convert(BuildState targetBuild, Map<BuildState, DefaultGradleBuild> all) {
        DefaultResilientGradleBuild model = (DefaultResilientGradleBuild) all.get(targetBuild);
        if (model != null) {
            return model;
        }
        model = new DefaultResilientGradleBuild();
        all.put(targetBuild, model);

        // Make sure the project tree has been loaded and can be queried (but not necessarily configured)
        ensureProjectsLoaded(targetBuild);

        Failure failure = brokenBuilds.get(targetBuild);
        if (failure != null) {
            model.setFailure(failure.toString());
        } else if (!brokenSettings.isEmpty()) {
            Map.Entry<SettingsInternal, Failure> settingsEntry = brokenSettings.entrySet().iterator().next();
            ProjectDescriptor rootProject = settingsEntry.getKey().getRootProject();
            BasicGradleProject root = convertRoot(targetBuild, rootProject);
            model.setRootProject(root);
            model.addProject(root);
            model.setFailure(settingsEntry.getValue().toString());
        }

        GradleInternal gradle = targetBuild.getMutableModel();
        if (targetBuild.isProjectsLoaded()) {
            addProjects(targetBuild, model);
        }
        try {
            addFailedBuilds(targetBuild, all, model);
            addIncludedBuilds(gradle, model, all);
        } catch (IllegalStateException e) {
            //ignore, happens when included builds are not accessible, but we need this for resiliency
        }
        iterateParents(targetBuild, all, gradle, model);
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

    private void addFailedBuilds(BuildState targetBuild, Map<BuildState, DefaultGradleBuild> all, DefaultGradleBuild model) {
        for (Map.Entry<BuildState, Failure> entry : brokenBuilds.entrySet()) {
            BuildState parent = entry.getKey().getParent();
            if (parent != null && parent.equals(targetBuild)) {
                DefaultGradleBuild failedBuild = convert(entry.getKey(), all);
                model.addIncludedBuild(failedBuild);
            }
        }
    }
}
