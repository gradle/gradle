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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;

import static org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultProjectLocalComponentProvider implements LocalComponentProvider {
    private final ProjectStateRegistry projectStateRegistry;
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final LocalComponentMetadataBuilder metadataBuilder;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildIdentifier thisBuild;
    private final LoadingCache<ProjectComponentIdentifier, LocalComponentMetadata> projects = CacheBuilder.newBuilder().build(new CacheLoader<ProjectComponentIdentifier, LocalComponentMetadata>() {
        @Override
        public LocalComponentMetadata load(ProjectComponentIdentifier projectIdentifier) {
            return getLocalComponentMetadata(projectIdentifier);
        }
    });

    public DefaultProjectLocalComponentProvider(ProjectStateRegistry projectStateRegistry, ProjectRegistry<ProjectInternal> projectRegistry, LocalComponentMetadataBuilder metadataBuilder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, BuildIdentifier thisBuild) {
        this.projectStateRegistry = projectStateRegistry;
        this.projectRegistry = projectRegistry;
        this.metadataBuilder = metadataBuilder;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.thisBuild = thisBuild;
    }

    public LocalComponentMetadata getComponent(ProjectComponentIdentifier projectIdentifier) {
        if (!isLocalProject(projectIdentifier)) {
            return null;
        }
        Object result;
        try {
            return projects.get(projectIdentifier);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private boolean isLocalProject(ProjectComponentIdentifier projectIdentifier) {
        return projectIdentifier.getBuild().equals(thisBuild);
    }

    private LocalComponentMetadata getLocalComponentMetadata(ProjectComponentIdentifier projectIdentifier) {
        // TODO - the project model should be reachable from ProjectState without another lookup
        final ProjectInternal project = projectRegistry.getProject(projectIdentifier.getProjectPath());
        if (project == null) {
            throw new IllegalArgumentException(projectIdentifier + " not found.");
        }
        return projectStateRegistry.stateFor(project).withMutableState(new Factory<LocalComponentMetadata>() {
            @Nullable
            @Override
            public LocalComponentMetadata create() {
                return getLocalComponentMetadata(project);
            }
        });
    }

    private LocalComponentMetadata getLocalComponentMetadata(ProjectInternal project) {
        Module module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier componentIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata metaData = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), (AttributesSchemaInternal) project.getDependencies().getAttributesSchema());
        for (ConfigurationInternal configuration : project.getConfigurations().withType(ConfigurationInternal.class)) {
            metadataBuilder.addConfiguration(metaData, configuration);
        }
        return metaData;
    }

}
