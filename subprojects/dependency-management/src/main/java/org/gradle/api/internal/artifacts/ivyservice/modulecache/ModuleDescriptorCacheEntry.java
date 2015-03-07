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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.MavenModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ModuleSource;

import java.math.BigInteger;

abstract class ModuleDescriptorCacheEntry {
    static final byte TYPE_MISSING = 0;
    static final byte TYPE_IVY = 1;
    static final byte TYPE_MAVEN = 2;

    final byte type;
    final boolean isChanging;
    final long createTimestamp;
    final ModuleSource moduleSource;
    final BigInteger moduleDescriptorHash;

    ModuleDescriptorCacheEntry(byte type, boolean isChanging, long createTimestamp, BigInteger moduleDescriptorHash, ModuleSource moduleSource) {
        this.type = type;
        this.isChanging = isChanging;
        this.createTimestamp = createTimestamp;
        this.moduleSource = moduleSource;
        this.moduleDescriptorHash = moduleDescriptorHash;
    }

    public static ModuleDescriptorCacheEntry forMissingModule(long createTimestamp) {
        return new MissingModuleCacheEntry(createTimestamp);
    }

    public static ModuleDescriptorCacheEntry forMetaData(ModuleComponentResolveMetaData metaData, long createTimestamp, BigInteger moduleDescriptorHash) {
        if (metaData instanceof IvyModuleResolveMetaData) {
            return new IvyModuleCacheEntry(metaData.isChanging(), createTimestamp, moduleDescriptorHash, metaData.getSource());
        }
        if (metaData instanceof MavenModuleResolveMetaData) {
            MavenModuleResolveMetaData mavenMetaData = (MavenModuleResolveMetaData) metaData;
            String packaging = mavenMetaData.getPackaging();
            String snapshotTimestamp = mavenMetaData.getSnapshotTimestamp();
            return new MavenModuleCacheEntry(metaData.isChanging(), packaging, snapshotTimestamp, createTimestamp, moduleDescriptorHash, metaData.getSource());
        }
        throw new IllegalArgumentException("Not a valid module version type: " + metaData);
    }

    public boolean isMissing() {
        return type == TYPE_MISSING;
    }
    
    public MutableModuleComponentResolveMetaData createMetaData(ModuleComponentIdentifier componentIdentifier, ModuleDescriptor descriptor) {
        throw new UnsupportedOperationException("Cannot create meta-data for entry " + this);
    }

    protected MutableModuleComponentResolveMetaData configure(MutableModuleComponentResolveMetaData input) {
        input.setChanging(isChanging);
        input.setSource(moduleSource);
        return input;
    }
}
