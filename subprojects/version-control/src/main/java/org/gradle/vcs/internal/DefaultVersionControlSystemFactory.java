/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal;

import org.gradle.api.GradleException;
import org.gradle.api.initialization.Settings;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CleanupActionFactory;
import org.gradle.cache.internal.FixedAgeOldestCacheCleanup;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.util.GFileUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.git.internal.GitVersionControlSystem;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;
import org.gradle.vcs.internal.spec.DirectoryRepositorySpec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultVersionControlSystemFactory implements VersionControlSystemFactory, Stoppable {
    private final PersistentCache vcsWorkingDirCache;
    private final BuildLayoutFactory buildLayoutFactory;

    DefaultVersionControlSystemFactory(VcsWorkingDirectoryRoot workingDirectoryRoot, CacheRepository cacheRepository, CleanupActionFactory cleanupActionFactory, BuildLayoutFactory buildLayoutFactory) {
        this.buildLayoutFactory = buildLayoutFactory;
        this.vcsWorkingDirCache = cacheRepository
            .cache(workingDirectoryRoot.getDir())
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withDisplayName("VCS Checkout Cache")
            .withCleanup(cleanupActionFactory.create(new FixedAgeOldestCacheCleanup(7L)))
            .open();
    }

    @Override
    public VersionControlSystem create(VersionControlSpec spec) {
        // TODO: Register these mappings somewhere
        VersionControlSystem vcs;
        if (spec instanceof DirectoryRepositorySpec) {
            vcs = new SimpleVersionControlSystem();
        } else {
            vcs = new GitVersionControlSystem();
        }
        return new LockingVersionControlSystem(
            new RequireSettingsVersionControlSystem(vcs, buildLayoutFactory),
            vcsWorkingDirCache);
    }

    @Override
    public void stop() {
        vcsWorkingDirCache.close();
    }

    private static final class RequireSettingsVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;
        private final BuildLayoutFactory buildLayoutFactory;

        private RequireSettingsVersionControlSystem(VersionControlSystem delegate, BuildLayoutFactory buildLayoutFactory) {
            this.delegate = delegate;
            this.buildLayoutFactory = buildLayoutFactory;
        }

        @Override
        public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
            return delegate.getAvailableVersions(spec);
        }

        @Override
        public File populate(File versionDir, VersionRef ref, VersionControlSpec spec) {
            File workingDir = delegate.populate(versionDir, ref, spec);
            PluginRequests pluginRequests = ((AbstractVersionControlSpec)spec).getPluginRequests();
            if (!pluginRequests.isEmpty() && buildLayoutFactory.findExistingSettingsFileIn(workingDir) == null) {
                // Injecting plugins and no settings file exists.
                // TODO: For now, just touch an empty file so this appears to be a well-formed build.
                // Eventually, we may want to relax this requirement for included builds
                GFileUtils.touch(new File(workingDir, Settings.DEFAULT_SETTINGS_FILE));
            }
            return workingDir;
        }
    }
    private static final class LockingVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;
        private final CacheAccess cacheAccess;

        private LockingVersionControlSystem(VersionControlSystem delegate, CacheAccess cacheAccess) {
            this.delegate = delegate;
            this.cacheAccess = cacheAccess;
        }

        @Override
        public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
            try {
                return delegate.getAvailableVersions(spec);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not list available versions for '%s'.", spec.getDisplayName()), e);
            }
        }

        @Override
        public File populate(final File versionDir, final VersionRef ref, final VersionControlSpec spec) {
            return cacheAccess.useCache(new Factory<File>() {
                @Nullable
                @Override
                public File create() {
                    GFileUtils.mkdirs(versionDir);
                    GFileUtils.touch(versionDir);
                    try {
                        return delegate.populate(versionDir, ref, spec);
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not populate %s from '%s'.", versionDir, spec.getDisplayName()), e);
                    }
                }
            });
        }
    }
}
