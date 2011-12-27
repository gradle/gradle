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
import org.gradle.ResolveMode;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

import java.io.File;

public class ResolveModeOverride {
    private final CachePolicy overridePolicy;
    private final ResolveMode resolveMode;

    public ResolveModeOverride(ResolveMode resolveMode) {
        this.resolveMode = resolveMode;
        this.overridePolicy = createOverridePolicy(resolveMode);
    }

    private CachePolicy createOverridePolicy(ResolveMode resolveMode) {
        switch (resolveMode) {
            case FORCE:
                return new ForceResolveCachePolicy(true);
            case OFFLINE:
                return new ForceResolveCachePolicy(false);
            default:
                return null;
        }
    }

    public CachePolicy overrideCachePolicy(CachePolicy original) {
        if (overridePolicy != null) {
            return overridePolicy;
        }
        return original;
    }

    public ModuleVersionRepository overrideModuleVersionRepository(ModuleVersionRepository original) {
        if (resolveMode == ResolveMode.OFFLINE) {
            return new OfflineModuleVersionRepository(original);
        }
        return original;
    }

    private static class ForceResolveCachePolicy implements CachePolicy {
        private final boolean mustRefresh;

        public ForceResolveCachePolicy(boolean mustRefresh) {
            this.mustRefresh = mustRefresh;
        }

        public boolean mustRefreshDynamicVersion(ResolvedModuleVersion version, long ageMillis) {
            return mustRefresh;
        }

        public boolean mustRefreshModule(ResolvedModuleVersion version, long ageMillis) {
            return mustRefresh;
        }

        public boolean mustRefreshChangingModule(ResolvedModuleVersion version, long ageMillis) {
            return mustRefresh;
        }

        public boolean mustRefreshMissingArtifact(long ageMillis) {
            return mustRefresh;
        }
    }

    private static class OfflineModuleVersionRepository implements ModuleVersionRepository {
        private final ModuleVersionRepository delegate;
        public OfflineModuleVersionRepository(ModuleVersionRepository delegate) {
            this.delegate = delegate;
        }

        public String getId() {
            return delegate.getId();
        }

        public boolean isLocal() {
            return delegate.isLocal();
        }

        public ModuleVersionDescriptor getDependency(DependencyDescriptor dd) {
            if (isLocal()) {
                return delegate.getDependency(dd);
            }
            throw new ModuleVersionResolveException("No cached version available for offline mode");
        }

        public File download(Artifact artifact) {
            if (isLocal()) {
                return delegate.download(artifact);
            }
            throw ArtifactResolutionExceptionBuilder.downloadFailure(artifact, "No cached version available for offline mode");
        }
    }
}
