/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

class CachedMetadataProvider implements MetadataProvider {
    private final BuildableModuleComponentMetaDataResolveResult cachedResult;
    private final ComponentMetadata cachedComponentMetadata;
    private final boolean usable;

    CachedMetadataProvider(BuildableModuleComponentMetaDataResolveResult result) {
        cachedResult = result;
        usable = cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
        if (usable) {
            cachedComponentMetadata = new ComponentMetadataAdapter(cachedResult.getMetaData());
        } else {
            cachedComponentMetadata = null;
        }
    }

    @Override
    public ComponentMetadata getComponentMetadata() {
        return cachedComponentMetadata;
    }

    @Override
    public IvyModuleDescriptor getIvyModuleDescriptor() {
        ModuleComponentResolveMetadata metaData = cachedResult.getMetaData();
        if (metaData instanceof IvyModuleResolveMetadata) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metaData;
            return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
        }
        return null;
    }

    @Override
    public boolean isUsable() {
        return usable;
    }
}
