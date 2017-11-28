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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.GradleModuleMetadataSource;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableComponentVariantResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import javax.inject.Inject;

/**
 * TODO: This class sources Gradle metadata files, but there's no such thing as a MutableGradleModuleComponentResolveMetadata yet:
 * dependending on the repository type, we will generate either Ivy or Maven module metadata. Ideally, this shouldn't be the case,
 * we should have a Gradle specific metadata format, and we shouldn't bound the component resolve metadata format to the repository
 * type. But this is for later. As a result, we need to pass in a factory that will create an "empty shell" for resolved metadata
 * that the Gradle module metadata parser will feed.
 */
public class DefaultGradleModuleMetadataSource extends AbstractMetadataSource<MutableModuleComponentResolveMetadata> implements GradleModuleMetadataSource {
    private final ExperimentalFeatures experimentalFeatures;
    private final ModuleMetadataParser metadataParser;
    private final MutableModuleMetadataFactory<MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory;

    @Inject
    public DefaultGradleModuleMetadataSource(ExperimentalFeatures experimentalFeatures, ModuleMetadataParser metadataParser, MutableModuleMetadataFactory<MutableModuleComponentResolveMetadata> mutableModuleMetadataFactory) {
        this.experimentalFeatures = experimentalFeatures;
        this.metadataParser = metadataParser;
        this.mutableModuleMetadataFactory = mutableModuleMetadataFactory;
    }

    @Override
    public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult result) {
        if (experimentalFeatures.isEnabled()) {
            LocallyAvailableExternalResource gradleMetadataArtifact = artifactResolver.resolveArtifact(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, new DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "module", "module")), result);
            if (gradleMetadataArtifact != null) {
                MutableModuleComponentResolveMetadata metaDataFromResource = mutableModuleMetadataFactory.create(moduleComponentIdentifier);
                metadataParser.parse(gradleMetadataArtifact, (MutableComponentVariantResolveMetadata) metaDataFromResource);
                return metaDataFromResource;
            }
        }
        return null;
    }

    @Override
    public void appendId(BuildCacheHasher hasher) {
        super.appendId(hasher);
        hasher.putBoolean(experimentalFeatures.isEnabled());
    }
}
