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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

public class MetadataProvider {
    private final ModuleComponentResolveState resolveState;
    private BuildableModuleComponentMetaDataResolveResult cachedResult;

    public MetadataProvider(ModuleComponentResolveState resolveState) {
        this.resolveState = resolveState;
    }

    public MetadataProvider(BuildableModuleComponentMetaDataResolveResult result) {
        this.resolveState = null;
        cachedResult = result;
    }

    public ComponentMetadata getComponentMetadata() {
        return new ComponentMetadataAdapter(getMetaData());
    }

    @Nullable
    public IvyModuleDescriptor getIvyModuleDescriptor() {
        ModuleComponentResolveMetadata metaData = getMetaData();
        if (metaData instanceof IvyModuleResolveMetadata) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metaData;
            return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
        }
        return null;
    }

    public boolean resolve() {
        if (cachedResult == null) {
            cachedResult = resolveState.resolve();
        }
        return cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    public ModuleComponentResolveMetadata getMetaData() {
        resolve();
        return cachedResult.getMetaData();
    }

    public boolean isUsable() {
        return cachedResult == null || cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    @Nullable
    public BuildableModuleComponentMetaDataResolveResult getResult() {
        return cachedResult;
    }
}
