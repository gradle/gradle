/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetaData;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;

import java.io.File;
import java.util.Set;

public class CompositeContextBuilder {
    private final DefaultCompositeBuildContext context = new DefaultCompositeBuildContext();
    private final GradleLauncherFactory launcherFactory;
    private final boolean propagateFailures;

    public CompositeContextBuilder(GradleLauncherFactory launcherFactory, boolean propagateFailures) {
        this.launcherFactory = launcherFactory;
        this.propagateFailures = propagateFailures;
    }

    public void addParticipant(File rootDir) {
        // TODO:DAZ Need to de-duplicate the build names
        String name = rootDir.getName();

        StartParameter startParameter = new StartParameter();
        startParameter.setSearchUpwards(false);
        startParameter.setProjectDir(rootDir);

        GradleLauncher gradleLauncher = launcherFactory.newInstance(startParameter);
        try {
            GradleInternal gradle = (GradleInternal) gradleLauncher.getBuildAnalysis().getGradle();
            for (Project project : gradle.getRootProject().getAllprojects()) {
                registerProject(name, (ProjectInternal) project);
            }
        } catch(ReportedException e) {
            // Ignore exceptions creating composite context
            // TODO:DAZ Handle this better. Test coverage.
            if (propagateFailures) {
                throw e;
            }
        } finally {
            gradleLauncher.stop();
        }
    }

    public void registerProject(String buildName, ProjectInternal project) {
        String projectPath = buildName + ":" + project.getPath();

        ModuleInternal module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(module);
        ComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(projectPath);
        DefaultLocalComponentMetaData localComponentMetaData = new DefaultLocalComponentMetaData(moduleVersionIdentifier, componentIdentifier, module.getStatus());
        addConfigurations(localComponentMetaData, project);
        context.register(moduleVersionIdentifier.getModule(), projectPath, localComponentMetaData);
    }

    private void addConfigurations(BuildableLocalComponentMetaData localComponentMetaData, ProjectInternal project) {
        for (Configuration configuration : project.getConfigurations()) {
            Set<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
            Set<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
            TaskDependency directBuildDependencies = new DefaultTaskDependency();
            localComponentMetaData.addConfiguration(configuration.getName(), configuration.getDescription(), extendsFrom, hierarchy, configuration.isVisible(), configuration.isTransitive(), directBuildDependencies);
        }
    }

    public CompositeBuildContext build() {
        return context;
    }
}
