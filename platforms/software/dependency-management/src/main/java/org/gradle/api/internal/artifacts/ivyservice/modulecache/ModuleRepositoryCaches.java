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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache;

public class ModuleRepositoryCaches {
    public final AbstractModuleVersionsCache moduleVersionsCache;
    public final AbstractModuleMetadataCache moduleMetadataCache;
    public final AbstractArtifactsCache moduleArtifactsCache;
    public final ModuleArtifactCache moduleArtifactCache;

    public ModuleRepositoryCaches(AbstractModuleVersionsCache moduleVersionsCache, AbstractModuleMetadataCache moduleMetadataCache, AbstractArtifactsCache moduleArtifactsCache, ModuleArtifactCache moduleArtifactCache) {
        this.moduleVersionsCache = moduleVersionsCache;
        this.moduleMetadataCache = moduleMetadataCache;
        this.moduleArtifactsCache = moduleArtifactsCache;
        this.moduleArtifactCache = moduleArtifactCache;
    }
}
