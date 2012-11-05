/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ChainedCachePolicy implements CachePolicy {
    private List<CachePolicy> cachePolicies;

    public ChainedCachePolicy(CachePolicy... cachePolicies) {
        this.cachePolicies = Arrays.asList(cachePolicies);
    }

    public boolean mustRefreshDynamicVersion(ModuleVersionSelector selector, ModuleVersionIdentifier moduleId, long ageMillis) {
        for (CachePolicy cachePolicy : cachePolicies) {
            if (cachePolicy.mustRefreshDynamicVersion(selector, moduleId, ageMillis)) {
                return true;
            }
        }
        return false;
    }

    public boolean mustRefreshModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion resolvedModuleVersion, ModuleRevisionId moduleRevisionId, long ageMillis) {
        for (CachePolicy cachePolicy : cachePolicies) {
            if (cachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis)) {
                return true;
            }
        }
        return false;
    }

    public boolean mustRefreshChangingModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        for (CachePolicy cachePolicy : cachePolicies) {
            if (cachePolicy.mustRefreshChangingModule(moduleVersionId, resolvedModuleVersion, ageMillis)) {
                return true;
            }
        }
        return false;
    }

    public boolean mustRefreshArtifact(ArtifactIdentifier artifactIdentifier, File cachedArtifactFile, long ageMillis) {
        for (CachePolicy cachePolicy : cachePolicies) {
            if (cachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, ageMillis)) {
                return true;
            }
        }
        return false;
    }
}
