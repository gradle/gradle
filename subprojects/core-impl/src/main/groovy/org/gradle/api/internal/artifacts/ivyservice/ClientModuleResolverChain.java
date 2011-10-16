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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * Resolver which looks for definitions first in defined Client Modules, before delegating to the user-defined resolver chain.
 * Artifact download is delegated to user-defined resolver chain.
 */
public class ClientModuleResolverChain implements DependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModuleResolverChain.class);
    private final ClientModuleResolver clientModuleResolver;
    private final DependencyResolver userResolverChain;
    private String name;

    public ClientModuleResolverChain(ClientModuleResolver clientModuleResolver, DependencyResolver userResolverChain) {
        this.clientModuleResolver = clientModuleResolver;
        this.userResolverChain = userResolverChain;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        ResolvedModuleRevision clientModuleDependency = clientModuleResolver.getDependency(dd, data);
        if (clientModuleDependency != null) {
            LOGGER.debug("Found client module:", clientModuleDependency);
            return clientModuleDependency;
        }
        return userResolverChain.getDependency(dd, data);
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return userResolverChain.locate(artifact);
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        return userResolverChain.download(artifact, options);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return userResolverChain.download(artifacts, options);
    }

    public boolean exists(Artifact artifact) {
        return userResolverChain.exists(artifact);
    }

    public OrganisationEntry[] listOrganisations() {
        return userResolverChain.listOrganisations();
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        return userResolverChain.listModules(org);
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        return userResolverChain.listRevisions(module);
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        return userResolverChain.listTokenValues(token, otherTokenValues);
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        return userResolverChain.listTokenValues(tokens, criteria);
    }

    public Namespace getNamespace() {
        return userResolverChain.getNamespace();
    }

    public void dumpSettings() {
    }

    public void setSettings(ResolverSettings settings) {
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return new NoOpRepositoryCacheManager(getName());
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return userResolverChain.findIvyFileRef(dd, data);
    }

    public void reportFailure() {
        userResolverChain.reportFailure();
    }

    public void reportFailure(Artifact art) {
        userResolverChain.reportFailure(art);
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void abortPublishTransaction() throws IOException {
        throw new UnsupportedOperationException();
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void commitPublishTransaction() throws IOException {
        throw new UnsupportedOperationException();
    }
}
