/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultProjectLocalComponentProvider implements ProjectLocalComponentProvider {
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final ConfigurationComponentMetaDataBuilder metaDataBuilder;
    private final ListMultimap<String, LocalComponentArtifactMetadata> registeredArtifacts = ArrayListMultimap.create();

    public DefaultProjectLocalComponentProvider(ProjectRegistry<ProjectInternal> projectRegistry, ConfigurationComponentMetaDataBuilder metaDataBuilder) {
        this.projectRegistry = projectRegistry;
        this.metaDataBuilder = metaDataBuilder;
    }

    public LocalComponentMetadata getComponent(ProjectComponentIdentifier projectIdentifier) {
        if (!isLocalProject(projectIdentifier)) {
            return null;
        }
        ProjectInternal project = projectRegistry.getProject(projectIdentifier.getProjectPath());
        if (project == null) {
            return null;
        }
        return getLocalComponentMetaData(project);
    }

    private boolean isLocalProject(ProjectComponentIdentifier projectIdentifier) {
        return projectIdentifier.getBuild().isCurrentBuild();
    }

    private LocalComponentMetadata getLocalComponentMetaData(ProjectInternal project) {
        Module module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(module);
        ComponentIdentifier componentIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata metaData = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), project.getAttributesSchema());
        metaDataBuilder.addConfigurations(metaData, project.getConfigurations().withType(ConfigurationInternal.class));
        return metaData;
    }

    @Override
    public void registerAdditionalArtifact(ProjectComponentIdentifier project, LocalComponentArtifactMetadata artifact) {
        if (!isLocalProject(project)) {
            return;
        }
        registeredArtifacts.put(project.getProjectPath(), artifact);
    }

    @Override
    public Iterable<LocalComponentArtifactMetadata> getAdditionalArtifacts(ProjectComponentIdentifier projectIdentifier) {
        if (!isLocalProject(projectIdentifier)) {
            return null;
        }
        String projectPath = projectIdentifier.getProjectPath();
        if (registeredArtifacts.containsKey(projectPath)) {
            return registeredArtifacts.get(projectPath);
        }
        return null;
    }

}
