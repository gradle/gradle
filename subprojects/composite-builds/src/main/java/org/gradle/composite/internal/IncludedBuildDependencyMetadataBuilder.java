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

package org.gradle.composite.internal;

import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.io.File;
import java.util.Set;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class IncludedBuildDependencyMetadataBuilder {
    private final CompositeBuildContext context;

    public IncludedBuildDependencyMetadataBuilder(CompositeBuildContext context) {
        this.context = context;
    }

    public void build(IncludedBuildInternal build) {
        Gradle gradle = build.getConfiguredBuild();
        for (Project project : gradle.getRootProject().getAllprojects()) {
            registerProject(build, (ProjectInternal) project);
        }
    }

    private void registerProject(IncludedBuild build, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);

        ProjectComponentIdentifier componentIdentifier = newProjectId(build, project.getPath());
        LocalComponentMetadata compositeComponent = createCompositeCopy(build, componentIdentifier, originalComponent);

        context.register(componentIdentifier, compositeComponent, project.getProjectDir());
        for (LocalComponentArtifactMetadata artifactMetaData : localComponentRegistry.getAdditionalArtifacts(originalIdentifier)) {
            context.registerAdditionalArtifact(componentIdentifier, createCompositeCopy(componentIdentifier, artifactMetaData));
        }
    }

    private LocalComponentMetadata createCompositeCopy(IncludedBuild build, ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetadata originalComponentMetadata) {
        DefaultLocalComponentMetadata compositeComponentMetadata = new DefaultLocalComponentMetadata(originalComponentMetadata.getId(), componentIdentifier, originalComponentMetadata.getStatus(), originalComponentMetadata.getAttributesSchema());

        for (String configurationName : originalComponentMetadata.getConfigurationNames()) {
            LocalConfigurationMetadata originalConfiguration = originalComponentMetadata.getConfiguration(configurationName);
            compositeComponentMetadata.addConfiguration(configurationName,
                originalConfiguration.getDescription(), originalConfiguration.getExtendsFrom(), originalConfiguration.getHierarchy(),
                originalConfiguration.isVisible(), originalConfiguration.isTransitive(), originalConfiguration.getAttributes(),
                originalConfiguration.isCanBeConsumed(),
                originalConfiguration.isCanBeResolved());

            Set<? extends LocalComponentArtifactMetadata> artifacts = originalConfiguration.getArtifacts();
            for (LocalComponentArtifactMetadata originalArtifact : artifacts) {
                File artifactFile = originalArtifact.getFile();
                Set<String> targetTasks = getArtifactTasks(originalArtifact);
                CompositeProjectComponentArtifactMetadata artifact = new CompositeProjectComponentArtifactMetadata(componentIdentifier, originalArtifact.getName(), artifactFile, targetTasks);
                compositeComponentMetadata.addArtifact(configurationName, artifact);
            }
        }

        for (LocalOriginDependencyMetadata dependency : originalComponentMetadata.getDependencies()) {
            if (dependency.getSelector() instanceof ProjectComponentSelector) {
                ProjectComponentSelector requested = (ProjectComponentSelector) dependency.getSelector();
                dependency = dependency.withTarget(DefaultProjectComponentSelector.newSelector(build, requested));
            }
            compositeComponentMetadata.addDependency(dependency);
        }
        for (Exclude exclude : originalComponentMetadata.getExcludeRules()) {
            compositeComponentMetadata.addExclude(exclude);
        }

        return compositeComponentMetadata;
    }

    private LocalComponentArtifactMetadata createCompositeCopy(ProjectComponentIdentifier project, LocalComponentArtifactMetadata artifactMetaData) {
        File artifactFile = artifactMetaData.getFile();
        return new CompositeProjectComponentArtifactMetadata(project, artifactMetaData.getName(), artifactFile, getArtifactTasks(artifactMetaData));
    }

    private Set<String> getArtifactTasks(ComponentArtifactMetadata artifactMetaData) {
        Set<String> taskPaths = Sets.newLinkedHashSet();
        Set<? extends Task> tasks = artifactMetaData.getBuildDependencies().getDependencies(null);
        for (Task task : tasks) {
            taskPaths.add(task.getPath());
        }
        return taskPaths;
    }
}
