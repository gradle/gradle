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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.List;
import java.util.Set;

public class CompositeProjectArtifactBuilder implements ProjectArtifactBuilder {
    private final GradleLauncherFactory gradleLauncherFactory;
    private final StartParameter requestedStartParameter;
    private final ServiceRegistry serviceRegistry;
    private final Set<ProjectComponentIdentifier> executingProjects = Sets.newHashSet();
    private final Multimap<ProjectComponentIdentifier, String> executedTasks = LinkedHashMultimap.create();

    public CompositeProjectArtifactBuilder(GradleLauncherFactory gradleLauncherFactory,
                                           StartParameter requestedStartParameter,
                                           ServiceRegistry serviceRegistry) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.requestedStartParameter = requestedStartParameter;
        this.serviceRegistry = serviceRegistry;
    }

    private synchronized void buildStarted(ProjectComponentIdentifier project) {
        if (!executingProjects.add(project)) {
            ProjectComponentSelector selector = new DefaultProjectComponentSelector(project.getProjectPath());
            throw new ModuleVersionResolveException(selector, "Dependency cycle including " + project);
        }
    }

    private synchronized void buildCompleted(ProjectComponentIdentifier project) {
        executingProjects.remove(project);
    }

    @Override
    public void build(ComponentArtifactMetaData artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetaData) {
            CompositeProjectComponentArtifactMetaData artifactMetaData = (CompositeProjectComponentArtifactMetaData) artifact;
            build(artifactMetaData.getComponentId(), artifactMetaData.getRootDirectory(), artifactMetaData.getTasks());
        }
    }

    private void build(ProjectComponentIdentifier project, File buildDirectory, Iterable<String> taskNames) {
        buildStarted(project);
        try {
            doBuild(project, buildDirectory, taskNames);
        } finally {
            buildCompleted(project);
        }
    }

    private void doBuild(ProjectComponentIdentifier project, File buildDirectory, Iterable<String> taskNames) {
        List<String> tasksToExecute = Lists.newArrayList();
        for (String taskName : taskNames) {
            if (executedTasks.put(project, taskName)) {
                tasksToExecute.add(taskName);
            }
        }
        if (tasksToExecute.isEmpty()) {
            return;
        }

        StartParameter param = requestedStartParameter.newInstance();
        param.setProjectDir(buildDirectory);
        param.setTaskNames(tasksToExecute);

        GradleLauncher launcher = gradleLauncherFactory.newInstance(param, serviceRegistry);
        try {
            launcher.run();
        } finally {
            launcher.stop();
        }
    }
}
