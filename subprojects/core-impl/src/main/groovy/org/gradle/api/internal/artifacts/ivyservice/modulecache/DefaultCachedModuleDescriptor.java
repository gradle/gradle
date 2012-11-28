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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.internal.TimeProvider;

import java.io.Serializable;
import java.math.BigInteger;

class DefaultCachedModuleDescriptor implements ModuleDescriptorCache.CachedModuleDescriptor, Serializable {
    private final ModuleDescriptor moduleDescriptor;
    private final BigInteger descriptorHash;
    private final boolean isChangingModule;
    private final long ageMillis;

    public DefaultCachedModuleDescriptor(ModuleDescriptorCacheEntry entry, ModuleDescriptor moduleDescriptor, TimeProvider timeProvider) {
        this.moduleDescriptor = moduleDescriptor;
        this.isChangingModule = entry.isChanging;
        this.descriptorHash = entry.moduleDescriptorHash;
        ageMillis = timeProvider.getCurrentTime() - entry.createTimestamp;
    }

    public boolean isMissing() {
        return moduleDescriptor == null;
    }

    public ResolvedModuleVersion getModuleVersion() {
        ModuleRevisionId moduleRevisionId = isMissing() ? null : moduleDescriptor.getModuleRevisionId();
        return new DefaultResolvedModuleVersion(moduleRevisionId);
    }

    public ModuleDescriptor getModuleDescriptor() {
        return moduleDescriptor;
    }

    public boolean isChangingModule() {
        return isChangingModule;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public BigInteger getDescriptorHash() {
        return descriptorHash;
    }
}
