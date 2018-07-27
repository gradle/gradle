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

package org.gradle.api.internal.artifacts.vcs;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VersionRef;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VcsVersionSelectionCache {
    private final Map<String, VersionRef> resolvedVersions = new ConcurrentHashMap<String, VersionRef>();
    private final Map<String, Set<VersionRef>> repositoryVersions = new ConcurrentHashMap<String, Set<VersionRef>>();
    private final Map<String, File> checkoutDirs = new ConcurrentHashMap<String, File>();

    @Nullable
    public Set<VersionRef> getVersions(VersionControlSpec spec) {
        return repositoryVersions.get(spec.getUniqueId());
    }

    public void putVersions(VersionControlSpec spec, Set<VersionRef> versions) {
        repositoryVersions.put(spec.getUniqueId(), ImmutableSet.copyOf(versions));
    }

    @Nullable
    public File getCheckoutDir(VersionControlSpec spec, VersionRef version) {
        String cacheKey = versionCacheKey(spec, version);
        return checkoutDirs.get(cacheKey);
    }

    public void putCheckoutDir(VersionControlSpec spec, VersionRef version, File dir) {
        String cacheKey = versionCacheKey(spec, version);
        checkoutDirs.put(cacheKey, dir);
    }

    private String versionCacheKey(VersionControlSpec spec, VersionRef version) {
        return spec.getUniqueId() + ":" + version.getCanonicalId();
    }

    @Nullable
    public VersionRef getResolvedVersion(VersionControlSpec spec, VersionConstraint constraint) {
        String cacheKey = constraintCacheKey(spec, constraint);
        return resolvedVersions.get(cacheKey);
    }

    public void putResolvedVersion(VersionControlSpec spec, VersionConstraint constraint, VersionRef versionRef) {
        String cacheKey = constraintCacheKey(spec, constraint);
        resolvedVersions.put(cacheKey, versionRef);
    }

    private String constraintCacheKey(VersionControlSpec spec, VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return spec.getUniqueId() + ":b:" + constraint.getBranch();
        }
        return spec.getUniqueId() + ":v:" + constraint.getRequiredVersion();
    }
}
