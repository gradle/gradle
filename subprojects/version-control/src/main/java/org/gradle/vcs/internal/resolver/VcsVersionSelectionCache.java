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

package org.gradle.vcs.internal.resolver;

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
    private final Map<String, File> resolvedVersions = new ConcurrentHashMap<String, File>();
    private final Map<String, Set<VersionRef>> repositoryVersions = new ConcurrentHashMap<String, Set<VersionRef>>();
    private final Map<String, File> checkoutDirs = new ConcurrentHashMap<String, File>();

    @Nullable
    public Set<VersionRef> getVersionsForRepo(VersionControlSpec spec) {
        return repositoryVersions.get(spec.getUniqueId());
    }

    public void putVersionsForRepo(VersionControlSpec spec, Set<VersionRef> versions) {
        repositoryVersions.put(spec.getUniqueId(), ImmutableSet.copyOf(versions));
    }

    @Nullable
    public File getWorkingDirForRevision(VersionControlSpec spec, VersionRef version) {
        String cacheKey = versionCacheKey(spec, version);
        return checkoutDirs.get(cacheKey);
    }

    public void putWorkingDirForRevision(VersionControlSpec spec, VersionRef version, File dir) {
        String cacheKey = versionCacheKey(spec, version);
        checkoutDirs.put(cacheKey, dir);
    }

    @Nullable
    public File getWorkingDirForSelector(VersionControlSpec spec, VersionConstraint constraint) {
        String cacheKey = constraintCacheKey(spec, constraint);
        return resolvedVersions.get(cacheKey);
    }

    public void putWorkingDirForSelector(VersionControlSpec spec, VersionConstraint constraint, File workingDir) {
        String cacheKey = constraintCacheKey(spec, constraint);
        resolvedVersions.put(cacheKey, workingDir);
    }

    private String versionCacheKey(VersionControlSpec spec, VersionRef version) {
        return spec.getUniqueId() + ":" + version.getCanonicalId();
    }

    private String constraintCacheKey(VersionControlSpec spec, VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return spec.getUniqueId() + ":b:" + constraint.getBranch();
        }
        return spec.getUniqueId() + ":v:" + constraint.getRequiredVersion();
    }
}
