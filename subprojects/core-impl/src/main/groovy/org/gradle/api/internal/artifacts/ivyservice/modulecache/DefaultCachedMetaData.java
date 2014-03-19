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
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleDescriptorAdapter;
import org.gradle.util.BuildCommencedTimeProvider;

import java.math.BigInteger;

class DefaultCachedMetaData implements ModuleMetaDataCache.CachedMetaData {
    private final ModuleSource moduleSource;
    private final BigInteger descriptorHash;
    private final long ageMillis;
    private final MutableModuleVersionMetaData metaData;

    public DefaultCachedMetaData(ModuleDescriptorCacheEntry entry, ModuleDescriptor moduleDescriptor, BuildCommencedTimeProvider timeProvider) {
        this.moduleSource = entry.moduleSource;
        this.descriptorHash = entry.moduleDescriptorHash;
        this.ageMillis = timeProvider.getCurrentTime() - entry.createTimestamp;
        if (moduleDescriptor == null) {
            metaData = null;
        } else {
            ModuleDescriptorAdapter moduleDescriptorAdapter = new ModuleDescriptorAdapter(moduleDescriptor);
            moduleDescriptorAdapter.setChanging(entry.isChanging);
            moduleDescriptorAdapter.setMetaDataOnly(entry.isMetaDataOnly);
            metaData = moduleDescriptorAdapter;
        }
    }

    public boolean isMissing() {
        return metaData == null;
    }

    public ModuleSource getModuleSource() {
        return moduleSource;
    }

    public ResolvedModuleVersion getModuleVersion() {
        return isMissing() ? null : new DefaultResolvedModuleVersion(getMetaData().getId());
    }

    public MutableModuleVersionMetaData getMetaData() {
        return metaData;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public BigInteger getDescriptorHash() {
        return descriptorHash;
    }
}
