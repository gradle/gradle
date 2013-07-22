/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult.State.*;

class CachedModuleVersionResult {
    private final BuildableModuleVersionMetaDataResolveResult.State state;
    private final ModuleDescriptor moduleDescriptor;
    private final boolean isChanging;
    private final ModuleSource moduleSource;
    private final ModuleVersionIdentifier id;

    public CachedModuleVersionResult(BuildableModuleVersionMetaDataResolveResult result) {
        this.state = result.getState();
        if (state == Resolved) {
            ModuleVersionMetaData metaData = result.getMetaData();
            this.id = metaData.getId();
            this.moduleDescriptor = metaData.getDescriptor();
            this.isChanging = metaData.isChanging();
            this.moduleSource = result.getModuleSource();
        } else {
            this.id = null;
            this.moduleDescriptor = null;
            this.isChanging = false;
            this.moduleSource = null;
        }
    }

    public boolean isCacheable() {
        return state == Missing || state == ProbablyMissing || state == Resolved;
    }

    public void supply(BuildableModuleVersionMetaDataResolveResult result) {
        assert isCacheable() : "Results are not cacheable, cannot supply the results.";
        if (state == Resolved) {
            result.resolved(id, moduleDescriptor, isChanging, moduleSource);
        } else if (state == Missing) {
            result.missing();
        } else if (state == ProbablyMissing) {
            result.probablyMissing();
        }
    }
}
