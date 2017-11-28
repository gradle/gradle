/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractArtifactMetadataSource<S extends  MutableModuleComponentResolveMetadata> extends AbstractMetadataSource<S> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private final String artifactType;
    private final String artifactExtension;

    protected AbstractArtifactMetadataSource(String artifactType, String artifactExtension) {
        this.artifactType = artifactType;
        this.artifactExtension = artifactExtension;
    }

    protected AbstractArtifactMetadataSource() {
        this("jar", "jar");
    }

    @Override
    public S create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult result) {
        S metaDataFromDefaultArtifact = createMetaDataFromDefaultArtifact(moduleComponentIdentifier, prescribedMetaData, artifactResolver, result);
        if (metaDataFromDefaultArtifact != null) {
            LOGGER.debug("Found artifact but no meta-data for module '{}' in repository '{}', using default meta-data.", moduleComponentIdentifier, repositoryName);
            metaDataFromDefaultArtifact.setSource(artifactResolver.getSource());
            result.resolved(metaDataFromDefaultArtifact.asImmutable());
            return metaDataFromDefaultArtifact;
        }
        return null;
    }

    private S createMetaDataFromDefaultArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata overrideMetadata, ExternalResourceArtifactResolver artifactResolver, ResourceAwareResolveResult result) {
        List<IvyArtifactName> artifacts = overrideMetadata.getArtifacts();
        for (IvyArtifactName artifact : getDependencyArtifactNames(moduleComponentIdentifier.getModule(), artifacts)) {
            if (artifactResolver.artifactExists(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, artifact), result)) {
                return createMissingComponentMetadata(moduleComponentIdentifier);
            }
        }
        return null;
    }

    private List<IvyArtifactName> getDependencyArtifactNames(String moduleName, List<IvyArtifactName> artifacts) {
        if (artifacts.isEmpty()) {
            IvyArtifactName defaultArtifact = new DefaultIvyArtifactName(moduleName, artifactType, artifactExtension);
            return ImmutableList.of(defaultArtifact);
        }
        return artifacts;
    }

    protected abstract S createMissingComponentMetadata(ModuleComponentIdentifier moduleComponentIdentifier);
}
