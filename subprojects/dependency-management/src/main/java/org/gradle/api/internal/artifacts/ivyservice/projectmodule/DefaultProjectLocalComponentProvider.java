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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilitiesHandlerInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultProjectLocalComponentProvider implements ProjectLocalComponentProvider {
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final LocalComponentMetadataBuilder metaDataBuilder;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildIdentifier thisBuild;

    public DefaultProjectLocalComponentProvider(ProjectRegistry<ProjectInternal> projectRegistry, LocalComponentMetadataBuilder metaDataBuilder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, BuildIdentifier thisBuild) {
        this.projectRegistry = projectRegistry;
        this.metaDataBuilder = metaDataBuilder;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.thisBuild = thisBuild;
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
        return projectIdentifier.getBuild().equals(thisBuild);
    }

    private LocalComponentMetadata getLocalComponentMetaData(ProjectInternal project) {
        Module module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ComponentIdentifier componentIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata metaData = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), (AttributesSchemaInternal) project.getDependencies().getAttributesSchema(), (CapabilitiesHandlerInternal) project.getDependencies().getCapabilities());
        metaDataBuilder.addConfigurations(metaData, project.getConfigurations().withType(ConfigurationInternal.class));
        return metaData;
    }

}
