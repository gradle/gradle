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
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.external.model.*;

import java.math.BigInteger;

class ModuleDescriptorCacheEntry {
    private static final byte TYPE_MISSING = 0;
    private static final byte TYPE_IVY = 1;
    private static final byte TYPE_MAVEN = 2;

    public byte type;
    public boolean isChanging;
    public String packaging;
    public long createTimestamp;
    public ModuleSource moduleSource;
    public BigInteger moduleDescriptorHash;

    ModuleDescriptorCacheEntry(byte type, boolean isChanging, String packaging, long createTimestamp, BigInteger moduleDescriptorHash, ModuleSource moduleSource) {
        this.type = type;
        this.isChanging = isChanging;
        this.packaging = packaging;
        this.createTimestamp = createTimestamp;
        this.moduleSource = moduleSource;
        this.moduleDescriptorHash = moduleDescriptorHash;
    }

    public static ModuleDescriptorCacheEntry forMissingModule(long createTimestamp) {
        return new ModuleDescriptorCacheEntry(TYPE_MISSING, false, null, createTimestamp, BigInteger.ZERO, null);
    }

    public static ModuleDescriptorCacheEntry forMetaData(ModuleComponentResolveMetaData metaData, long createTimestamp, BigInteger moduleDescriptorHash) {
        byte type = getType(metaData);
        String packaging = getPackaging(metaData);
        return new ModuleDescriptorCacheEntry(type, metaData.isChanging(), packaging, createTimestamp, moduleDescriptorHash, metaData.getSource());
    }

    private static String getPackaging(ModuleComponentResolveMetaData metaData) {
        return metaData instanceof MavenModuleResolveMetaData ? ((MavenModuleResolveMetaData) metaData).getPackaging() : null;
    }

    private static byte getType(ModuleComponentResolveMetaData metaData) {
        if (metaData == null) {
            return TYPE_MISSING;
        }
        if (metaData instanceof IvyModuleResolveMetaData) {
            return TYPE_IVY;
        }
        if (metaData instanceof MavenModuleResolveMetaData) {
            return TYPE_MAVEN;
        }
        throw new IllegalArgumentException("Not a valid module version type: " + metaData);
    }

    public boolean isMissing() {
        return type == TYPE_MISSING;
    }
    
    public MutableModuleComponentResolveMetaData createMetaData(ModuleDescriptor descriptor) {
        switch (type) {
            case TYPE_IVY:
                return configure(new DefaultIvyModuleResolveMetaData(descriptor));
            case TYPE_MAVEN:
                // TODO Relocation is not currently cached
                return configure(new DefaultMavenModuleResolveMetaData(descriptor, packaging, false));
            case TYPE_MISSING:
            default:
                return null;
        }
    }

    private MutableModuleComponentResolveMetaData configure(MutableModuleComponentResolveMetaData input) {
        input.setChanging(isChanging);
        input.setSource(moduleSource);
        return input;
    }
}
