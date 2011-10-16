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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * Entry point to the resolvers. The delegate contains all the other resolvers.
 * <p>
 * by Szczepan Faber, created at: 10/7/11
 */
public class EntryPointResolver implements DependencyResolver {

    private final DependencyResolver delegate;
    private String name;
    private IvyResolutionListener ivyResolutionListener;

    public EntryPointResolver(DependencyResolver delegate) {
        assert delegate != null : "Delegate cannot be null!";
        this.delegate = delegate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        assert ivyResolutionListener != null : "ivyResolutionListener was not configured";
        ivyResolutionListener.beforeMetadataResolved(dd, data);
        return delegate.getDependency(dd, data);
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return delegate.locate(artifact);
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        return delegate.download(artifact, options);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return delegate.download(artifacts, options);
    }

    public boolean exists(Artifact artifact) {
        return delegate.exists(artifact);
    }

    public OrganisationEntry[] listOrganisations() {
        return delegate.listOrganisations();
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        return delegate.listModules(org);
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        return delegate.listRevisions(module);
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        return delegate.listTokenValues(token, otherTokenValues);
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        return delegate.listTokenValues(tokens, criteria);
    }

    public Namespace getNamespace() {
        return delegate.getNamespace();
    }

    public void dumpSettings() {
    }

    public void setSettings(ResolverSettings settings) {
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return new NoOpRepositoryCacheManager(getName());
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return delegate.findIvyFileRef(dd, data);
    }

    public void reportFailure() {
        delegate.reportFailure();
    }

    public void reportFailure(Artifact art) {
        delegate.reportFailure(art);
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

    public void setIvyResolutionListener(IvyResolutionListener ivyResolutionListener) {
        this.ivyResolutionListener = ivyResolutionListener;
    }
}
