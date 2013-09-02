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
import org.apache.ivy.plugins.latest.LatestStrategy;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleVersionRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A wrapper over a {@link org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver} that is exposed through the DSL,
 * for backwards compatibility.
 */
public class LegacyDependencyResolver implements DependencyResolver, ResolutionAwareRepository {
    private final ExternalResourceResolver resolver;
    private ResolverSettings settings;

    public LegacyDependencyResolver(ExternalResourceResolver resolver) {
        this.resolver = resolver;
    }

    public String getName() {
        return resolver.getName();
    }

    public void setName(String name) {
        resolver.setName(name);
    }

    public String toString() {
        return resolver.toString();
    }

    public ConfiguredModuleVersionRepository createResolver() {
        return resolver;
    }

    public void setSettings(ResolverSettings settings) {
        this.settings = settings;
    }

    public ResolverSettings getSettings() {
        return settings;
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

    public ArtifactOrigin locate(Artifact artifact) {
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

    public void addIvyPattern(String pattern) {
        resolver.addIvyPattern(pattern);
    }

    public void addArtifactPattern(String pattern) {
        resolver.addArtifactPattern(pattern);
    }

    public List<String> getIvyPatterns() {
        return resolver.getIvyPatterns();
    }

    public List<String> getArtifactPatterns() {
        return resolver.getArtifactPatterns();
    }

    public void dumpSettings() {
        // this is not used
        throw new UnsupportedOperationException();
    }

    public boolean isM2compatible() {
        return resolver.isM2compatible();
    }

    public void setM2compatible(boolean compatible) {
        resolver.setM2compatible(compatible);
    }

    public boolean isCheckconsistency() {
        return resolver.isCheckconsistency();
    }

    public void setCheckconsistency(boolean checkConsistency) {
        resolver.setCheckconsistency(checkConsistency);
    }

    public void setForce(boolean force) {
        resolver.setForce(force);
    }

    public boolean isForce() {
        return resolver.isForce();
    }

    public boolean isAllownomd() {
        return resolver.isAllownomd();
    }

    public void setAllownomd(boolean b) {
        resolver.setAllownomd(b);
    }

    /**
     * Sets the module descriptor presence rule.
     * Should be one of {@link org.apache.ivy.plugins.resolver.BasicResolver#DESCRIPTOR_REQUIRED} or {@link org.apache.ivy.plugins.resolver.BasicResolver#DESCRIPTOR_OPTIONAL}.
     *
     * @param descriptorRule the descriptor rule to use with this resolver.
     */
    public void setDescriptor(String descriptorRule) {
        if (BasicResolver.DESCRIPTOR_REQUIRED.equals(descriptorRule)) {
            setAllownomd(false);
        } else if (BasicResolver.DESCRIPTOR_OPTIONAL.equals(descriptorRule)) {
            setAllownomd(true);
        } else {
            throw new IllegalArgumentException(
                "unknown descriptor rule '" + descriptorRule
                + "'. Allowed rules are: "
                + Arrays.asList(BasicResolver.DESCRIPTOR_REQUIRED, BasicResolver.DESCRIPTOR_OPTIONAL));
        }
    }

    public String[] getChecksumAlgorithms() {
        return resolver.getChecksumAlgorithms();
    }

    public void setChecksums(String checksums) {
        resolver.setChecksums(checksums);
    }

    public LatestStrategy getLatestStrategy() {
        throw new UnsupportedOperationException("getLatestStrategy");
    }

    public void setLatestStrategy(LatestStrategy latestStrategy) {
        throw new UnsupportedOperationException("setLatestStrategy");
    }

    public String getLatest() {
        throw new UnsupportedOperationException("getLatest");
    }

    public void setLatest(String strategyName) {
        throw new UnsupportedOperationException("setLatest");
    }

    public void setChangingMatcher(String changingMatcherName) {
        resolver.setChangingMatcher(changingMatcherName);
    }

    protected String getChangingMatcherName() {
        return resolver.getChangingMatcherName();
    }

    public void setChangingPattern(String changingPattern) {
        resolver.setChangingPattern(changingPattern);
    }

    protected String getChangingPattern() {
        return resolver.getChangingPattern();
    }

    public void setCheckmodified(boolean check) {
        throw new UnsupportedOperationException();
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        // This is never used
        throw new UnsupportedOperationException();
    }
}
