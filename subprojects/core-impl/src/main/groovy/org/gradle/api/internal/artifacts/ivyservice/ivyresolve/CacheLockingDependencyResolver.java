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

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.util.UncheckedException;

import java.text.ParseException;
import java.util.Arrays;

/**
 * A wrapper around an Ivy {@link org.apache.ivy.plugins.resolver.DependencyResolver} that handles locking/unlocking the cache.
 */
public class CacheLockingDependencyResolver extends DelegatingDependencyResolver {
    private final CacheLockingManager cacheLockingManager;

    public CacheLockingDependencyResolver(DependencyResolver resolver, CacheLockingManager cacheLockingManager) {
        super(resolver);
        this.cacheLockingManager = cacheLockingManager;
    }

    public ResolvedModuleRevision getDependency(final DependencyDescriptor dd, final ResolveData data) {
        return cacheLockingManager.longRunningOperation(String.format("Resolve %s using resolver %s", dd, getName()), new Factory<ResolvedModuleRevision>() {
            public ResolvedModuleRevision create() {
                try {
                    return getResolver().getDependency(dd, data);
                } catch (ParseException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
        });
    }

    public DownloadReport download(final Artifact[] artifacts, final DownloadOptions options) {
        return cacheLockingManager.longRunningOperation(String.format("Download %s using resolver %s", Arrays.toString(artifacts), getName()), new Factory<DownloadReport>() {
            public DownloadReport create() {
                return getResolver().download(artifacts, options);
            }
        });
    }

    public ArtifactDownloadReport download(final ArtifactOrigin artifact, final DownloadOptions options) {
        return cacheLockingManager.longRunningOperation(String.format("Download %s using resolver %s", artifact, getName()), new Factory<ArtifactDownloadReport>() {
            public ArtifactDownloadReport create() {
                return getResolver().download(artifact, options);
            }
        });
    }

    public ResolvedResource findIvyFileRef(final DependencyDescriptor dd, final ResolveData data) {
        return cacheLockingManager.longRunningOperation(String.format("Find Ivy file for %s using resolver %s", dd, getName()), new Factory<ResolvedResource>() {
            public ResolvedResource create() {
                return getResolver().findIvyFileRef(dd, data);
            }
        });
    }

    public ArtifactOrigin locate(final Artifact artifact) {
        return cacheLockingManager.longRunningOperation(String.format("Locate %s using resolver %s", artifact, getName()), new Factory<ArtifactOrigin>() {
            public ArtifactOrigin create() {
                return getResolver().locate(artifact);
            }
        });
    }

    public boolean exists(final Artifact artifact) {
        return cacheLockingManager.longRunningOperation(String.format("Check for %s using resolver %s", artifact, getName()), new Factory<Boolean>() {
            public Boolean create() {
                return getResolver().exists(artifact);
            }
        });
    }
}
