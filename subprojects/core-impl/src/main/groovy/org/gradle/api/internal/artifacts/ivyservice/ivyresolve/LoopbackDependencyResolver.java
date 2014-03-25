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
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.search.ModuleEntry;
import org.apache.ivy.core.search.OrganisationEntry;
import org.apache.ivy.core.search.RevisionEntry;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData;
import org.gradle.internal.Factory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * The main entry point for a {@link DependencyResolver} to call back into the dependency resolution mechanism.
 */
public class LoopbackDependencyResolver implements DependencyResolver {
    private final String name;
    private final DependencyToModuleVersionResolver dependencyResolver;
    private final ArtifactResolver artifactResolver;
    private final CacheLockingManager cacheLockingManager;

    public LoopbackDependencyResolver(String name, RepositoryChain repositoryChain, CacheLockingManager cacheLockingManager) {
        this.name = name;
        this.dependencyResolver = repositoryChain.getDependencyResolver();
        this.artifactResolver = repositoryChain.getArtifactResolver();
        this.cacheLockingManager = cacheLockingManager;
    }

    public String getName() {
        return name;
    }

    public void setSettings(ResolverSettings settings) {
        // don't care
    }

    public ResolvedModuleRevision getDependency(final DependencyDescriptor dd, final ResolveData data) throws ParseException {
        final DependencyResolver loopback = this;
        return cacheLockingManager.useCache(String.format("Resolve %s", dd), new Factory<ResolvedModuleRevision>() {
            public ResolvedModuleRevision create() {
                DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
                DefaultDependencyMetaData dependency = new DefaultDependencyMetaData(dd);
                IvyContext ivyContext = IvyContext.pushNewCopyContext();
                try {
                    ivyContext.setResolveData(data);
                    dependencyResolver.resolve(dependency, result);
                } finally {
                    IvyContext.popContext();
                }
                return new ResolvedModuleRevision(loopback, loopback, result.getMetaData().getDescriptor(), null);
            }
        });
    }

    public ArtifactOrigin locate(final Artifact artifact) {
        return cacheLockingManager.useCache(String.format("Locate %s", artifact), new Factory<ArtifactOrigin>() {
            public ArtifactOrigin create() {
                try {
                    DependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(artifact.getModuleRevisionId(), false);
                    DefaultBuildableComponentResolveResult resolveResult = new DefaultBuildableComponentResolveResult();
                    DefaultDependencyMetaData dependency = new DefaultDependencyMetaData(dependencyDescriptor);
                    dependencyResolver.resolve(dependency, resolveResult);
                    DefaultBuildableArtifactResolveResult artifactResolveResult = new DefaultBuildableArtifactResolveResult();
                    ComponentArtifactMetaData artifactMetaData = resolveResult.getMetaData().artifact(artifact);
                    artifactResolver.resolveArtifact(artifactMetaData, resolveResult.getMetaData().getSource(), artifactResolveResult);
                    File artifactFile = artifactResolveResult.getFile();
                    return new ArtifactOrigin(artifact, false, artifactFile.getAbsolutePath());
                } catch (ModuleVersionNotFoundException e) {
                    return null;
                } catch (ArtifactNotFoundException e) {
                    return null;
                }
            }
        });
    }

    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    public void abortPublishTransaction() throws IOException {
        throw new UnsupportedOperationException();
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        throw new UnsupportedOperationException();
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        throw new UnsupportedOperationException();
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        throw new UnsupportedOperationException();
    }

    public boolean exists(Artifact artifact) {
        throw new UnsupportedOperationException();
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void commitPublishTransaction() throws IOException {
        throw new UnsupportedOperationException();
    }

    public void reportFailure() {
        throw new UnsupportedOperationException();
    }

    public void reportFailure(Artifact art) {
        throw new UnsupportedOperationException();
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        throw new UnsupportedOperationException();
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        throw new UnsupportedOperationException();
    }

    public OrganisationEntry[] listOrganisations() {
        throw new UnsupportedOperationException();
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        throw new UnsupportedOperationException();
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        throw new UnsupportedOperationException();
    }

    public Namespace getNamespace() {
        throw new UnsupportedOperationException();
    }

    public void dumpSettings() {
        throw new UnsupportedOperationException();
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        throw new UnsupportedOperationException();
    }
}
