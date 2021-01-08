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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

/**
 * Provides the metadata for a component consumed from the same build that produces it.
 *
 * Currently, the metadata for a component is different based on whether it is consumed from the producing build or from another build. This difference should go away.
 */
public class DefaultProjectLocalComponentProvider implements LocalComponentProvider {
    private final ProjectStateRegistry projectStateRegistry;
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final LocalComponentMetadataBuilder metadataBuilder;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildIdentifier thisBuild;
    private final Cache<ProjectComponentIdentifier, LocalComponentMetadata> projects = CacheBuilder.newBuilder().build();

    public DefaultProjectLocalComponentProvider(ProjectStateRegistry projectStateRegistry, ProjectRegistry<ProjectInternal> projectRegistry, LocalComponentMetadataBuilder metadataBuilder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, BuildIdentifier thisBuild) {
        this.projectStateRegistry = projectStateRegistry;
        this.projectRegistry = projectRegistry;
        this.metadataBuilder = metadataBuilder;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.thisBuild = thisBuild;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier projectIdentifier) {
        if (!isLocalProject(projectIdentifier)) {
            return null;
        }
        try {
            // Resolving a project component can cause traversal to other projects, at which
            // point we could release the project lock and allow another task to run.  We can't
            // use a cache loader here because it is synchronized.  If the other task also tries
            // to resolve a project component, he can block trying to get the lock around the
            // loader while still holding the project lock.  To avoid this deadlock, we check,
            // then release the project lock only if we need to resolve the project and ensure
            // that only the thread holding the lock can populate the metadata for a project.
            LocalComponentMetadata metadata = projects.getIfPresent(projectIdentifier);
            if (metadata == null) {
                metadata = getLocalComponentMetadata(projectIdentifier);
            }
            return metadata;
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private boolean isLocalProject(ProjectComponentIdentifier projectIdentifier) {
        return projectIdentifier.getBuild().equals(thisBuild);
    }

    private LocalComponentMetadata getLocalComponentMetadata(final ProjectComponentIdentifier projectIdentifier) {
        ProjectState projectState = projectStateRegistry.stateFor(projectIdentifier);
        return projectState.fromMutableState(p -> {
            LocalComponentMetadata metadata = projects.getIfPresent(projectIdentifier);
            if (metadata == null) {
                metadata = getLocalComponentMetadata(projectState, p);
                projects.put(projectIdentifier, metadata);
            }
            return metadata;
        });
    }

    private LocalComponentMetadata getLocalComponentMetadata(ProjectState projectState, ProjectInternal project) {
        Module module = project.getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier componentIdentifier = projectState.getComponentIdentifier();
        DefaultLocalComponentMetadata metaData = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), (AttributesSchemaInternal) project.getDependencies().getAttributesSchema());
        for (ConfigurationInternal configuration : project.getConfigurations().withType(ConfigurationInternal.class)) {
            metadataBuilder.addConfiguration(metaData, configuration);
        }
        return metaData;
    }
}
