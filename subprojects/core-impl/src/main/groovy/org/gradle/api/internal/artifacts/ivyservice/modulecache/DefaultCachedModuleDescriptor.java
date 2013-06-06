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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.internal.TimeProvider;

import java.math.BigInteger;

class DefaultCachedModuleDescriptor implements ModuleDescriptorCache.CachedModuleDescriptor {
    private final ModuleDescriptor moduleDescriptor;
    private final ModuleSource moduleSource;
    private final BigInteger descriptorHash;
    private final boolean isChangingModule;
    private final long ageMillis;

    public DefaultCachedModuleDescriptor(ModuleDescriptorCacheEntry entry, ModuleDescriptor moduleDescriptor, TimeProvider timeProvider) {
        this.moduleDescriptor = moduleDescriptor;
        this.moduleSource = entry.moduleSource;
        this.isChangingModule = entry.isChanging;
        this.descriptorHash = entry.moduleDescriptorHash;
        this.ageMillis = timeProvider.getCurrentTime() - entry.createTimestamp;
    }

    public boolean isMissing() {
        return moduleDescriptor == null;
    }

    public ModuleSource getModuleSource() {
        return moduleSource;
    }

    public ResolvedModuleVersion getModuleVersion() {
        ModuleVersionIdentifier moduleVersionIdentifier = isMissing() ? null : DefaultModuleVersionIdentifier.newId(moduleDescriptor.getModuleRevisionId());
        return new DefaultResolvedModuleVersion(moduleVersionIdentifier);
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
