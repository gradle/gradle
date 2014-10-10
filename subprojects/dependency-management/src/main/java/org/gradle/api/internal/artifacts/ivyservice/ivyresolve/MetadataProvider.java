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
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;

public class MetadataProvider {
    private final Factory<? extends MutableModuleComponentResolveMetaData> metaDataSupplier;
    private MutableModuleComponentResolveMetaData cachedMetaData;

    public MetadataProvider(Factory<? extends MutableModuleComponentResolveMetaData> metaDataSupplier) {
        this.metaDataSupplier = metaDataSupplier;
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

    public MutableModuleComponentResolveMetaData getMetaData() {
        if (cachedMetaData == null) {
            cachedMetaData = metaDataSupplier.create();
        }
        return cachedMetaData;
    }
}
