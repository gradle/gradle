/*
 * Copyright 2011 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.internal.Factory;

/**
 * A wrapper around a {@link ModuleVersionRepository} that handles locking/unlocking the cache.
 */
public class CacheLockingModuleVersionRepository implements ModuleVersionRepository {
    private final ModuleVersionRepository repository;
    private final CacheLockingManager cacheLockingManager;

    public CacheLockingModuleVersionRepository(ModuleVersionRepository repository, CacheLockingManager cacheLockingManager) {
        this.repository = repository;
        this.cacheLockingManager = cacheLockingManager;
    }

    public String getId() {
        return repository.getId();
    }

    public String getName() {
        return repository.getName();
    }

    public boolean isLocal() {
        return repository.isLocal();
    }

    public ModuleVersionDescriptor getDependency(final DependencyDescriptor dd) throws ModuleVersionResolveException {
        return cacheLockingManager.longRunningOperation(String.format("Resolve %s using repository %s", dd, getId()), new Factory<ModuleVersionDescriptor>() {
            public ModuleVersionDescriptor create() {
                return repository.getDependency(dd);
            }
        });
    }

    public void download(final Artifact artifact, final BuildableArtifactResolveResult result) {
        cacheLockingManager.longRunningOperation(String.format("Download %s using repository %s", artifact, getId()), new Runnable() {
            public void run() {
                repository.download(artifact, result);
            }
        });
    }
}
