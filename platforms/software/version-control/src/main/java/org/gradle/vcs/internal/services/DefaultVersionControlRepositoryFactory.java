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

package org.gradle.vcs.internal.services;

import org.gradle.api.GradleException;
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.nio.ModificationTimeFileAccessTimeJournal;
import org.gradle.util.internal.GFileUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.git.internal.GitVersionControlSystem;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;
import org.gradle.vcs.internal.VersionControlRepositoryConnectionFactory;
import org.gradle.vcs.internal.VersionControlSystem;
import org.gradle.vcs.internal.VersionRef;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

import static org.gradle.api.internal.cache.CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES;
import static org.gradle.internal.hash.Hashing.hashString;
import static org.gradle.internal.time.TimestampSuppliers.daysAgo;

class DefaultVersionControlRepositoryFactory implements VersionControlRepositoryConnectionFactory, Stoppable {
    private final PersistentCache vcsWorkingDirCache;

    DefaultVersionControlRepositoryFactory(BuildTreeScopedCacheBuilderFactory cacheBuilderFactory, CacheCleanupStrategyFactory cacheCleanupStrategyFactory) {
        this.vcsWorkingDirCache = cacheBuilderFactory
            .createCrossVersionCacheBuilder("vcs-1")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .withDisplayName("VCS Checkout Cache")
            .withCleanupStrategy(cacheCleanupStrategyFactory.daily(
                new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(1), new ModificationTimeFileAccessTimeJournal(), daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES))
            ))
            .open();
    }

    @Override
    public VersionControlRepositoryConnection create(VersionControlSpec spec) {
        // TODO: Register these mappings somewhere
        VersionControlSystem vcs;
        if (!(spec instanceof GitVersionControlSpec)) {
            throw new IllegalArgumentException(String.format("Don't know how to create a VCS from spec %s.", spec));
        }
        vcs = new GitVersionControlSystem();

        GitVersionControlSpec gitSpec = (GitVersionControlSpec) spec;
        gitSpec.getUrl().finalizeValue();

        return new LockingVersionControlRepository(gitSpec, vcs, vcsWorkingDirCache);
    }

    @Override
    public void stop() {
        vcsWorkingDirCache.close();
    }

    private static final class LockingVersionControlRepository implements VersionControlRepositoryConnection {
        private final VersionControlSpec spec;
        private final VersionControlSystem delegate;
        private final PersistentCache cacheAccess;

        private LockingVersionControlRepository(VersionControlSpec spec, VersionControlSystem delegate, PersistentCache cacheAccess) {
            this.spec = spec;
            this.delegate = delegate;
            this.cacheAccess = cacheAccess;
        }

        @Override
        public String getDisplayName() {
            return spec.getDisplayName();
        }

        @Override
        public String getUniqueId() {
            return spec.getUniqueId().get();
        }

        @Override
        public VersionRef getDefaultBranch() {
            try {
                return delegate.getDefaultBranch(spec);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not locate default branch for %s.", spec.getDisplayName()), e);
            }
        }

        @Nullable
        @Override
        public VersionRef getBranch(String branch) {
            try {
                return delegate.getBranch(spec, branch);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not locate branch '%s' for %s.", branch, spec.getDisplayName()), e);
            }
        }

        @Override
        public Set<VersionRef> getAvailableVersions() {
            try {
                return delegate.getAvailableVersions(spec);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not list available versions for %s.", spec.getDisplayName()), e);
            }
        }

        @Override
        public File populate(final VersionRef ref) {
            return cacheAccess.useCache(() -> {
                try {
                    String repoName = spec.getRepoName().get();
                    String prefix = repoName.length() <= 9 ? repoName : repoName.substring(0, 10);
                    String versionId = prefix + "_" + hashString(getUniqueId() + "-" + ref.getCanonicalId()).toCompactString();
                    File baseDir = new File(cacheAccess.getBaseDir(), versionId);
                    File workingDir = new File(baseDir, repoName);
                    GFileUtils.mkdirs(workingDir);
                    // Update timestamp so that working directory is not garbage collected
                    GFileUtils.touch(baseDir);
                    delegate.populate(workingDir, ref, spec);
                    return workingDir;
                } catch (Exception e) {
                    throw new GradleException(String.format("Could not populate working directory from %s.", spec.getDisplayName()), e);
                }
            });
        }
    }
}
