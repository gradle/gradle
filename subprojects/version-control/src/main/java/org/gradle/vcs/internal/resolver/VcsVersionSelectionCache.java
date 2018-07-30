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
import org.gradle.vcs.internal.VersionControlRepositoryConnection;
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
    public Set<VersionRef> getVersionsForRepo(VersionControlRepositoryConnection repository) {
        return repositoryVersions.get(repository.getUniqueId());
    }

    public void putVersionsForRepo(VersionControlRepositoryConnection repository, Set<VersionRef> versions) {
        repositoryVersions.put(repository.getUniqueId(), ImmutableSet.copyOf(versions));
    }

    @Nullable
    public File getWorkingDirForRevision(VersionControlRepositoryConnection repository, VersionRef version) {
        String cacheKey = versionCacheKey(repository, version);
        return checkoutDirs.get(cacheKey);
    }

    public void putWorkingDirForRevision(VersionControlRepositoryConnection repository, VersionRef version, File dir) {
        String cacheKey = versionCacheKey(repository, version);
        checkoutDirs.put(cacheKey, dir);
    }

    @Nullable
    public File getWorkingDirForSelector(VersionControlRepositoryConnection repository, VersionConstraint constraint) {
        String cacheKey = constraintCacheKey(repository, constraint);
        return resolvedVersions.get(cacheKey);
    }

    public void putWorkingDirForSelector(VersionControlRepositoryConnection repository, VersionConstraint constraint, File workingDir) {
        String cacheKey = constraintCacheKey(repository, constraint);
        resolvedVersions.put(cacheKey, workingDir);
    }

    private String versionCacheKey(VersionControlRepositoryConnection repository, VersionRef version) {
        return repository.getUniqueId() + ":" + version.getCanonicalId();
    }

    private String constraintCacheKey(VersionControlRepositoryConnection repository, VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return repository.getUniqueId() + ":b:" + constraint.getBranch();
        }
        return repository.getUniqueId() + ":v:" + constraint.getRequiredVersion();
    }
}
