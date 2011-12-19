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
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;

import java.io.File;
import java.text.ParseException;

/**
 * The main entry point for a {@link DependencyResolver} to call back into the dependency
 * resolution mechanism.
 */
public class LoopbackDependencyResolver extends LimitedDependencyResolver {
    private final String name;
    private final UserResolverChain resolver;
    private final CacheLockingManager cacheLockingManager;

    public LoopbackDependencyResolver(String name, UserResolverChain resolver, CacheLockingManager cacheLockingManager) {
        this.name = name;
        this.resolver = resolver;
        this.cacheLockingManager = cacheLockingManager;
    }

    public String getName() {
        return name;
    }

    public ResolvedModuleRevision getDependency(final DependencyDescriptor dd, final ResolveData data) throws ParseException {
        return cacheLockingManager.useCache(String.format("Resolve %s", dd), new Factory<ResolvedModuleRevision>() {
            public ResolvedModuleRevision create() {
                return resolver.getDependency(dd, data);
            }
        });
    }

    public ArtifactOrigin locate(final Artifact artifact) {
        return cacheLockingManager.useCache(String.format("Locate %s", artifact), new Factory<ArtifactOrigin>() {
            public ArtifactOrigin create() {
                try {
                    File artifactFile = resolver.resolve(artifact);
                    return new ArtifactOrigin(artifact, false, artifactFile.getAbsolutePath());
                } catch (ArtifactNotFoundException e) {
                    return null;
                }
            }
        });
    }

    public UserResolverChain getUserResolverChain() {
        return resolver;
    }
}
