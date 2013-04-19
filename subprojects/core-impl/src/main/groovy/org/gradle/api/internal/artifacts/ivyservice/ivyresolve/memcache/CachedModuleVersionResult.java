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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.Missing;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.ProbablyMissing;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData.State.Resolved;

/**
* By Szczepan Faber on 4/19/13
*/
class CachedModuleVersionResult {
    private final BuildableModuleVersionMetaData.State state;
    private final ModuleDescriptor moduleDescriptor;
    private final boolean isChanging;
    private final ModuleSource moduleSource;
    private final ModuleVersionIdentifier id;

    public CachedModuleVersionResult(BuildableModuleVersionMetaData result) {
        this.state = result.getState();
        if (state == Resolved) {
            this.id = result.getId();
            this.moduleDescriptor = result.getDescriptor();
            this.isChanging = result.isChanging();
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

    public void supply(BuildableModuleVersionMetaData result) {
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
