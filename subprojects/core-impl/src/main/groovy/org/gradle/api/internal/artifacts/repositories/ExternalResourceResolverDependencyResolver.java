/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

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
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * Adapts an ExternalResourceResolver to an ivy DependencyResolver, for internal use (in parsing).
 * The plan is for this to go.
 */
public class ExternalResourceResolverDependencyResolver implements DependencyResolver {
    private final ExternalResourceResolver resolver;

    public ExternalResourceResolverDependencyResolver(ExternalResourceResolver resolver) {
        this.resolver = resolver;
    }

    public String getName() {
        return resolver.getName();
    }

    public String toString() {
        return resolver.toString();
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return resolver.locate(artifact);
    }

    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    public void setSettings(ResolverSettings ivy) {
        throw new UnsupportedOperationException();
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        // This is not used
        throw new UnsupportedOperationException();
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        // This is not used
        throw new UnsupportedOperationException();
    }

    public boolean exists(Artifact artifact) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public ArtifactDownloadReport download(ArtifactOrigin origin, DownloadOptions options) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void reportFailure() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void reportFailure(Artifact art) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public OrganisationEntry[] listOrganisations() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public RevisionEntry[] listRevisions(ModuleEntry mod) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void abortPublishTransaction() throws IOException {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void commitPublishTransaction() throws IOException {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public Namespace getNamespace() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public void dumpSettings() {
        // this is not used
        throw new UnsupportedOperationException();
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        // This is never used
        throw new UnsupportedOperationException();
    }
}
