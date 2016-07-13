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

package org.gradle.api.internal.composite;

import com.google.common.collect.Sets;
import org.gradle.api.Buildable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;

import java.io.File;
import java.util.Set;

public class CompositeContextBuilder implements BuildActionRunner {
    private final DefaultBuildableCompositeBuildContext context = new DefaultBuildableCompositeBuildContext();
    private final boolean propagateFailures;

    public CompositeContextBuilder(boolean propagateFailures) {
        this.propagateFailures = propagateFailures;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        try {
            GradleInternal gradle = buildController.configure();
            ProjectInternal rootProject = gradle.getRootProject();

            String participantName = rootProject.getName();
            for (Project project : rootProject.getAllprojects()) {
                registerProject(participantName, (ProjectInternal) project);
            }
        } catch (ReportedException e) {
            // Ignore exceptions creating composite context for a model request
            // TODO:DAZ Retain this configuration failure for subsequent model requests for this build
            if (propagateFailures) {
                throw e;
            }
        }
    }

    private void registerProject(String buildName, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = DefaultProjectComponentIdentifier.newId(project.getPath());
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);

        ProjectComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(createExternalProjectPath(buildName, project.getPath()));
        LocalComponentMetadata compositeComponent = createCompositeCopy(buildName, componentIdentifier, originalComponent);

        context.register(componentIdentifier, compositeComponent, project.getProjectDir());
        for (ComponentArtifactMetadata artifactMetaData : localComponentRegistry.getAdditionalArtifacts(originalIdentifier)) {
            context.registerAdditionalArtifact(componentIdentifier, createCompositeCopy(componentIdentifier, artifactMetaData));
        }
    }

    private LocalComponentMetadata createCompositeCopy(String buildName, ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetadata originalComponentMetadata) {
        DefaultLocalComponentMetadata compositeComponentMetadata = new DefaultLocalComponentMetadata(originalComponentMetadata.getId(), componentIdentifier, originalComponentMetadata.getStatus());

        for (String configurationName : originalComponentMetadata.getConfigurationNames()) {
            LocalConfigurationMetadata originalConfiguration = originalComponentMetadata.getConfiguration(configurationName);
            compositeComponentMetadata.addConfiguration(configurationName,
                originalConfiguration.getDescription(), originalConfiguration.getExtendsFrom(), originalConfiguration.getHierarchy(),
                originalConfiguration.isVisible(), originalConfiguration.isTransitive(), new DefaultTaskDependency());

            final Set<String> targetTasks = determineTargetTasks(originalConfiguration);

            Set<ComponentArtifactMetadata> artifacts = originalConfiguration.getArtifacts();
            for (ComponentArtifactMetadata originalArtifact : artifacts) {
                File artifactFile = ((LocalComponentArtifactIdentifier) originalArtifact).getFile();
                CompositeProjectComponentArtifactMetadata artifact = new CompositeProjectComponentArtifactMetadata(componentIdentifier, originalArtifact.getName(), artifactFile, targetTasks);
                compositeComponentMetadata.addArtifact(configurationName, artifact);
            }
        }

        for (DependencyMetadata dependency : originalComponentMetadata.getDependencies()) {
            if (dependency.getSelector() instanceof ProjectComponentSelector) {
                ProjectComponentSelector requested = (ProjectComponentSelector) dependency.getSelector();
                String externalPath = createExternalProjectPath(buildName, requested.getProjectPath());
                ProjectComponentSelector externalizedSelector = DefaultProjectComponentSelector.newSelector(externalPath);
                dependency = dependency.withTarget(externalizedSelector);
            }
            compositeComponentMetadata.addDependency(dependency);
        }
        for (Exclude exclude : originalComponentMetadata.getExcludeRules()) {
            compositeComponentMetadata.addExclude(exclude);
        }

        return compositeComponentMetadata;
    }

    private String createExternalProjectPath(String buildName, String projectPath) {
        return buildName + ":" + projectPath;
    }

    private ComponentArtifactMetadata createCompositeCopy(ProjectComponentIdentifier project, ComponentArtifactMetadata artifactMetaData) {
        File artifactFile = ((LocalComponentArtifactIdentifier) artifactMetaData).getFile();
        return new CompositeProjectComponentArtifactMetadata(project, artifactMetaData.getName(), artifactFile, getArtifactTasks(artifactMetaData));
    }

    public CompositeBuildContext build() {
        return context;
    }

    private Set<String> determineTargetTasks(LocalConfigurationMetadata configuration) {
        Set<String> taskNames = Sets.newLinkedHashSet();
        for (ComponentArtifactMetadata artifactMetaData : configuration.getArtifacts()) {
            addArtifactTasks(taskNames, artifactMetaData);
        }
        return taskNames;
    }

    private Set<String> getArtifactTasks(ComponentArtifactMetadata artifactMetaData) {
        Set<String> taskNames = Sets.newLinkedHashSet();
        addArtifactTasks(taskNames, artifactMetaData);
        return taskNames;
    }

    private void addArtifactTasks(Set<String> taskNames, ComponentArtifactMetadata artifactMetaData) {
        if (artifactMetaData instanceof Buildable) {
            Buildable publishArtifact = (Buildable) artifactMetaData;
            Set<? extends Task> dependencies = publishArtifact.getBuildDependencies().getDependencies(null);
            for (Task dependency : dependencies) {
                taskNames.add(dependency.getPath());
            }
        }
    }
}
