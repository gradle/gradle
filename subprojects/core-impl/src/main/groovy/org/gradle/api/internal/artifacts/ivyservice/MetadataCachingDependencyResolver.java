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

public class MetadataCachingDependencyResolver implements DependencyResolver {
    private final DependencyResolver resolver;

    public MetadataCachingDependencyResolver(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    public String getName() {
        return resolver.getName();
    }

    public void setName(String name) {
        resolver.setName(name);
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        return resolver.getDependency(dd, data);
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return resolver.findIvyFileRef(dd, data);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return resolver.download(artifacts, options);
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        return resolver.download(artifact, options);
    }

    public boolean exists(Artifact artifact) {
        return resolver.exists(artifact);
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return resolver.locate(artifact);
    }

    public void reportFailure() {
        resolver.reportFailure();
    }

    public void reportFailure(Artifact art) {
        resolver.reportFailure(art);
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        return resolver.listTokenValues(token, otherTokenValues);
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        return resolver.listTokenValues(tokens, criteria);
    }

    public OrganisationEntry[] listOrganisations() {
        return resolver.listOrganisations();
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        return resolver.listModules(org);
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        return resolver.listRevisions(module);
    }

    public void abortPublishTransaction() throws IOException {
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

    public Namespace getNamespace() {
        return resolver.getNamespace();
    }

    public void dumpSettings() {
        resolver.dumpSettings();
    }

    public void setSettings(ResolverSettings settings) {
        resolver.setSettings(settings);
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return resolver.getRepositoryCacheManager();
    }
}
