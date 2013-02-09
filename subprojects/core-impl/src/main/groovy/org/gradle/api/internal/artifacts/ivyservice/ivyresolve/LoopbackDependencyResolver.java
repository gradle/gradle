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

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableModuleVersionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException;
import org.gradle.internal.Factory;

import java.io.File;
import java.text.ParseException;

/**
 * The main entry point for a {@link DependencyResolver} to call back into the dependency resolution mechanism.
 */
public class LoopbackDependencyResolver extends RestrictedDependencyResolver {
    private final String name;
    private final UserResolverChain userResolverChain;
    private final CacheLockingManager cacheLockingManager;

    public LoopbackDependencyResolver(String name, UserResolverChain userResolverChain, CacheLockingManager cacheLockingManager) {
        this.name = name;
        this.userResolverChain = userResolverChain;
        this.cacheLockingManager = cacheLockingManager;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setSettings(ResolverSettings settings) {
        userResolverChain.setSettings(settings);
    }

    @Override
    public ResolvedModuleRevision getDependency(final DependencyDescriptor dd, final ResolveData data) throws ParseException {
        final DependencyResolver loopback = this;
        return cacheLockingManager.useCache(String.format("Resolve %s", dd), new Factory<ResolvedModuleRevision>() {
            public ResolvedModuleRevision create() {
                DefaultBuildableModuleVersionResolveResult dependency = new DefaultBuildableModuleVersionResolveResult();
                IvyContext ivyContext = IvyContext.pushNewCopyContext();
                try {
                    ivyContext.setResolveData(data);
                    userResolverChain.resolve(dd, dependency);
                } finally {
                    IvyContext.popContext();
                }
                return new ResolvedModuleRevision(loopback, loopback, dependency.getDescriptor(), null);
            }
        });
    }

    @Override
    public ArtifactOrigin locate(final Artifact artifact) {
        return cacheLockingManager.useCache(String.format("Locate %s", artifact), new Factory<ArtifactOrigin>() {
            public ArtifactOrigin create() {
                try {
                    DependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(artifact.getModuleRevisionId(), false);
                    DefaultBuildableModuleVersionResolveResult dependency = new DefaultBuildableModuleVersionResolveResult();
                    userResolverChain.resolve(dependencyDescriptor, dependency);
                    DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
                    dependency.getArtifactResolver().resolve(artifact, result);
                    File artifactFile = result.getFile();
                    return new ArtifactOrigin(artifact, false, artifactFile.getAbsolutePath());
                } catch (ModuleVersionNotFoundException e) {
                    return null;
                } catch (ArtifactNotFoundException e) {
                    return null;
                }
            }
        });
    }
}
