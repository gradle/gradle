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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableComponentVariantResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import javax.annotation.Nullable;

public abstract class GradleMetadataAwareExternalResourceResolver<T extends ModuleComponentResolveMetadata, S extends MutableModuleComponentResolveMetadata & MutableComponentVariantResolveMetadata> extends ExternalResourceResolver<T, S> {
    private final boolean useGradleMetadata;
    private final ModuleMetadataParser metadataParser;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    protected GradleMetadataAwareExternalResourceResolver(String name, boolean local, ExternalResourceRepository repository, CacheAwareExternalResourceAccessor cachingResourceAccessor, VersionLister versionLister, LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository, boolean useGradleMetadata, ModuleMetadataParser metadataParser, ImmutableModuleIdentifierFactory moduleIdentifierFactory1) {
        super(name, local, repository, cachingResourceAccessor, versionLister, locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, fileResourceRepository);
        this.useGradleMetadata = useGradleMetadata;
        this.metadataParser = metadataParser;
        this.moduleIdentifierFactory = moduleIdentifierFactory1;
    }

    public boolean isUseGradleMetadata() {
        return useGradleMetadata;
    }

    @Nullable
    @Override
    protected S parseMetaDataFromArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ExternalResourceArtifactResolver artifactResolver, ResourceAwareResolveResult result) {
        S metadata = super.parseMetaDataFromArtifact(moduleComponentIdentifier, artifactResolver, result);
        if (useGradleMetadata) {
            LocallyAvailableExternalResource resource = artifactResolver.resolveArtifact(new DefaultModuleComponentArtifactMetadata(moduleComponentIdentifier, new DefaultIvyArtifactName(moduleComponentIdentifier.getModule(), "module", "module")), result);
            if (resource != null) {
                // Use default empty metadata when the Ivy file isn't present
                if (metadata == null) {
                    ModuleVersionIdentifier mvi = moduleIdentifierFactory.moduleWithVersion(moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());
                    metadata = metadata(mvi, moduleComponentIdentifier);
                }
                metadataParser.parse(resource, metadata);
            }
        }
        return metadata;
    }

    abstract S metadata(ModuleVersionIdentifier id, ModuleComponentIdentifier cid);

}
