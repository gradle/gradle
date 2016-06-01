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

import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;

import java.io.File;
import java.util.Collections;

public class LocalProjectComponentProvider implements ProjectComponentProvider {
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final ConfigurationComponentMetaDataBuilder metaDataBuilder;

    public LocalProjectComponentProvider(ProjectRegistry<ProjectInternal> projectRegistry, ConfigurationComponentMetaDataBuilder metaDataBuilder) {
        this.projectRegistry = projectRegistry;
        this.metaDataBuilder = metaDataBuilder;
    }

    public LocalComponentMetaData getProject(ProjectComponentIdentifier projectIdentifier) {
        ProjectInternal project = projectRegistry.getProject(projectIdentifier.getProjectPath());
        if (project == null) {
            return null;
        }
        return getLocalComponentMetaData(project);
    }

    private LocalComponentMetaData getLocalComponentMetaData(ProjectInternal project) {
        Module module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(module);
        ComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(project.getPath());
        DefaultLocalComponentMetaData metaData = new DefaultLocalComponentMetaData(moduleVersionIdentifier, componentIdentifier, module.getStatus());
        metaDataBuilder.addConfigurations(metaData, project.getConfigurations());
        return metaData;
    }

    @Override
    public Iterable<ComponentArtifactMetaData> getAdditionalArtifacts(ProjectComponentIdentifier projectIdentifier) {
        ProjectInternal project = projectRegistry.getProject(projectIdentifier.getProjectPath());
        if (project == null || project.getExtensions().findByName("idea") == null) {
            return Collections.emptyList();
        }
        return Collections.singleton(createImlArtifact(projectIdentifier, project));
    }

    private ComponentArtifactMetaData createImlArtifact(ProjectComponentIdentifier projectId, ProjectInternal project) {
        String name = project.getName();
        File imlFile = new File(project.getProjectDir(), name + ".iml");
        String taskName = project.getPath().equals(":") ? ":ideaModule" : project.getPath() + ":ideaModule";
        Task byName = project.getTasks().getByPath(taskName);
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name, "iml", "iml", null, null, imlFile, byName);
        return new PublishArtifactLocalArtifactMetaData(projectId, "IML-FILE", publishArtifact);
    }
}
