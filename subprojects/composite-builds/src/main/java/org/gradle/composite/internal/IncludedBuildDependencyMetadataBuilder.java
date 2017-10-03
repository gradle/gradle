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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class IncludedBuildDependencyMetadataBuilder {
    public Map<ProjectComponentIdentifier, RegisteredProject> build(IncludedBuildInternal build) {
        Map<ProjectComponentIdentifier, RegisteredProject> registeredProjects = Maps.newHashMap();
        Gradle gradle = build.getConfiguredBuild();
        for (Project project : gradle.getRootProject().getAllprojects()) {
            registerProject(registeredProjects, build, (ProjectInternal) project);
        }
        return registeredProjects;
    }

    private void registerProject(Map<ProjectComponentIdentifier, RegisteredProject> registeredProjects, IncludedBuild build, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);

        ProjectComponentIdentifier componentIdentifier = newProjectId(build, project.getPath());
        LocalComponentMetadata compositeComponent = createCompositeCopy(build, componentIdentifier, originalComponent);
        List<LocalComponentArtifactMetadata> artifacts = Lists.newArrayList();
        for (LocalComponentArtifactMetadata artifactMetaData : localComponentRegistry.getAdditionalArtifacts(originalIdentifier)) {
            artifacts.add(createCompositeCopy(componentIdentifier, artifactMetaData));
        }
        registeredProjects.put(componentIdentifier, new RegisteredProject(compositeComponent, artifacts));
    }

    private LocalComponentMetadata createCompositeCopy(final IncludedBuild build, final ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetadata originalComponentMetadata) {
        return originalComponentMetadata.copy(componentIdentifier, new Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata>() {
            @Override
            public LocalComponentArtifactMetadata transform(LocalComponentArtifactMetadata originalArtifact) {
                File artifactFile = originalArtifact.getFile();
                Set<String> targetTasks = getArtifactTasks(originalArtifact);
                return new CompositeProjectComponentArtifactMetadata(componentIdentifier, originalArtifact.getName(), artifactFile, targetTasks);
            }
        }, new Transformer<LocalOriginDependencyMetadata, LocalOriginDependencyMetadata>() {
            @Override
            public LocalOriginDependencyMetadata transform(LocalOriginDependencyMetadata originalDependency) {
                if (originalDependency.getSelector() instanceof ProjectComponentSelector) {
                    ProjectComponentSelector requested = (ProjectComponentSelector) originalDependency.getSelector();
                    return originalDependency.withTarget(DefaultProjectComponentSelector.newSelector(build, requested));
                }
                return originalDependency;
            }
        });
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
