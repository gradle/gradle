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

import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.util.BuildCommencedTimeProvider;

import javax.annotation.Nullable;

class DefaultCachedMetadata implements ModuleMetadataCache.CachedMetadata {
    private final ModuleSource moduleSource;
    private final long ageMillis;
    private final ModuleComponentResolveMetadata metadata;
    private ModuleComponentResolveMetadata processedMetadata;

    public DefaultCachedMetadata(ModuleMetadataCacheEntry entry, ModuleComponentResolveMetadata metadata, BuildCommencedTimeProvider timeProvider) {
        this.moduleSource = entry.moduleSource;
        this.ageMillis = timeProvider.getCurrentTime() - entry.createTimestamp;
        this.metadata = metadata;
    }

    public boolean isMissing() {
        return metadata == null;
    }

    public ModuleSource getModuleSource() {
        return moduleSource;
    }

    public ResolvedModuleVersion getModuleVersion() {
        return isMissing() ? null : new DefaultResolvedModuleVersion(getMetadata().getModuleVersionId());
    }

    public ModuleComponentResolveMetadata getMetadata() {
        return metadata;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    @Nullable
    @Override
    public ModuleComponentResolveMetadata getProcessedMetadata() {
        return processedMetadata;
    }

    @Override
    public void setProcessedMetadata(ModuleComponentResolveMetadata processedMetadata) {
        this.processedMetadata = processedMetadata;
    }
}
