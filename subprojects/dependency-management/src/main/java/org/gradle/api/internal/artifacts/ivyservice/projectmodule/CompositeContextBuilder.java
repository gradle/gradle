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

import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Buildable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.local.model.LocalConfigurationMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;

import java.io.File;
import java.util.Set;

public class CompositeContextBuilder implements BuildActionRunner {
    private final DefaultCompositeBuildContext context = new DefaultCompositeBuildContext();
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
            // Ignore exceptions creating composite context
            // TODO:DAZ Handle this better. Test coverage.
            if (propagateFailures) {
                throw e;
            }
        }
    }

    private void registerProject(String buildName, ProjectInternal project) {
        String projectPath = buildName + ":" + project.getPath();
        ProjectComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(projectPath);

        ProjectComponentRegistry projectComponentRegistry = project.getServices().get(ProjectComponentRegistry.class);

        DefaultLocalComponentMetaData projectComponentMetadata = (DefaultLocalComponentMetaData) projectComponentRegistry.getProject(project.getPath());
        LocalComponentMetaData localComponentMetaData = createCompositeCopy(componentIdentifier, projectComponentMetadata);

        context.register(localComponentMetaData.getId().getModule(), projectPath, localComponentMetaData, project.getProjectDir());
    }

    private LocalComponentMetaData createCompositeCopy(ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetaData originalComponentMetadata) {
        DefaultLocalComponentMetaData compositeComponentMetadata = new DefaultLocalComponentMetaData(originalComponentMetadata.getId(), componentIdentifier, originalComponentMetadata.getStatus());

        for (String configurationName : originalComponentMetadata.getConfigurationNames()) {
            LocalConfigurationMetaData originalConfiguration = originalComponentMetadata.getConfiguration(configurationName);
            compositeComponentMetadata.addConfiguration(configurationName,
                originalConfiguration.getDescription(), originalConfiguration.getExtendsFrom(), originalConfiguration.getHierarchy(),
                originalConfiguration.isVisible(), originalConfiguration.isTransitive(), new DefaultTaskDependency());

            final Set<String> targetTaskNames = determineTargetTaskNames(originalConfiguration);

            Set<ComponentArtifactMetaData> artifacts = originalConfiguration.getArtifacts();
            for (ComponentArtifactMetaData originalArtifact : artifacts) {
                File artifactFile = ((LocalComponentArtifactIdentifier) originalArtifact).getFile();
                CompositeProjectComponentArtifactMetaData artifact = new CompositeProjectComponentArtifactMetaData(componentIdentifier, originalArtifact.getName(), artifactFile, targetTaskNames);
                compositeComponentMetadata.addArtifact(configurationName, artifact);
            }
        }

        for (DependencyMetaData dependency : originalComponentMetadata.getDependencies()) {
            // TODO:DAZ It doesn't seem right to be converting back to an external module selector here.
            if (dependency.getSelector() instanceof ProjectComponentSelector) {
                ModuleComponentSelector externalizedSelector = DefaultModuleComponentSelector.newSelector(dependency.getRequested());
                dependency = dependency.withTarget(externalizedSelector);
            }
            compositeComponentMetadata.addDependency(dependency);
        }
        for (ExcludeRule excludeRule : originalComponentMetadata.getExcludeRules()) {
            compositeComponentMetadata.addExcludeRule(excludeRule);
        }

        return compositeComponentMetadata;
    }

    public CompositeBuildContext build() {
        return context;
    }

    private Set<String> determineTargetTaskNames(LocalConfigurationMetaData configuration) {
        Set<String> taskNames = Sets.newLinkedHashSet();
        for (ComponentArtifactMetaData artifactMetaData : configuration.getArtifacts()) {
            if (artifactMetaData instanceof Buildable) {
                Buildable publishArtifact = (Buildable) artifactMetaData;
                Set<? extends Task> dependencies = publishArtifact.getBuildDependencies().getDependencies(null);
                for (Task dependency : dependencies) {
                    taskNames.add(dependency.getPath());
                }
            }
        }
        return taskNames;
    }
}
