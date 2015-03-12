/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;

public class MetadataProvider {
    private final Factory<? extends BuildableModuleComponentMetaDataResolveResult> metaDataSupplier;
    private BuildableModuleComponentMetaDataResolveResult cachedResult;

    public MetadataProvider(Factory<? extends BuildableModuleComponentMetaDataResolveResult> metaDataSupplier) {
        this.metaDataSupplier = metaDataSupplier;
    }

    public MetadataProvider(BuildableModuleComponentMetaDataResolveResult result) {
        this.metaDataSupplier = null;
        cachedResult = result;
    }

    public ComponentMetadata getComponentMetadata() {
        return new ComponentMetadataDetailsAdapter(getMetaData());
    }

    public IvyModuleDescriptor getIvyModuleDescriptor() {
        ModuleComponentResolveMetaData metaData = getMetaData();
        if (metaData instanceof IvyModuleResolveMetaData) {
            IvyModuleResolveMetaData ivyMetadata = (IvyModuleResolveMetaData) metaData;
            return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
        }
        return null;
    }

    public boolean resolve() {
        if (cachedResult == null) {
            cachedResult = metaDataSupplier.create();
        }
        return cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    public MutableModuleComponentResolveMetaData getMetaData() {
        resolve();
        return cachedResult.hasResult() ? cachedResult.getMetaData() : null;
    }

    public void applyTo(ResourceAwareResolveResult target) {
        if (cachedResult != null) {
            cachedResult.applyTo(target);
        }
    }

    public static class MetaDataSupplier implements Factory<BuildableModuleComponentMetaDataResolveResult> {
        private final DependencyMetaData dependency;
        private final ModuleComponentIdentifier id;
        private final ModuleComponentRepositoryAccess repository;

        public MetaDataSupplier(DependencyMetaData dependency, ModuleComponentIdentifier id, ModuleComponentRepositoryAccess repository) {
            this.dependency = dependency;
            this.id = id;
            this.repository = repository;
        }

        public BuildableModuleComponentMetaDataResolveResult create() {
            BuildableModuleComponentMetaDataResolveResult result = new DefaultBuildableModuleComponentMetaDataResolveResult();
            repository.resolveComponentMetaData(dependency.withRequestedVersion(id.getVersion()), id, result);
            return result;
        }
    }
}
