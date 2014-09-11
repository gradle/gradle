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

import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

import static org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.State.*;

class CachedModuleVersionResult {
    private final BuildableModuleComponentMetaDataResolveResult.State state;
    private final MutableModuleComponentResolveMetaData metaData;

    public CachedModuleVersionResult(BuildableModuleComponentMetaDataResolveResult result) {
        this.state = result.getState();
        if (state == Resolved) {
            this.metaData = result.getMetaData().copy();
        } else {
            this.metaData = null;
        }
    }

    public boolean isCacheable() {
        return state == Missing || state == ProbablyMissing || state == Resolved;
    }

    public void supply(BuildableModuleComponentMetaDataResolveResult result) {
        assert isCacheable() : "Results are not cacheable, cannot supply the results.";
        if (state == Resolved) {
            MutableModuleComponentResolveMetaData metaData = this.metaData.copy();
            result.resolved(metaData);
        } else if (state == Missing) {
            result.missing();
        } else if (state == ProbablyMissing) {
            result.probablyMissing();
        }
    }
}
