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
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Factory;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.git.internal.GitVersionControlSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultVersionControlSystemFactory implements VersionControlSystemFactory {
    private final CacheRepository cacheRepository;

    DefaultVersionControlSystemFactory(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
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
        return new LockingVersionControlSystem(vcs, cacheRepository);
    }

    private static final class LockingVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;
        private final CacheRepository cacheRepository;

        private LockingVersionControlSystem(VersionControlSystem delegate, CacheRepository cacheRepository) {
            this.delegate = delegate;
            this.cacheRepository = cacheRepository;
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
            PersistentCache cache = cacheRepository
                .cache(versionDir)
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();
            try {
                return cache.useCache(new Factory<File>() {
                    @Nullable
                    @Override
                    public File create() {
                        return delegate.populate(versionDir, ref, spec);
                    }
                });
            } catch (Exception e) {
                throw new GradleException(String.format("Could not populate %s from '%s'.", versionDir, spec.getDisplayName()), e);
            } finally {
                cache.close();
            }
        }
    }
}
