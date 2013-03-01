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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.CacheDownloadOptions;
import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.search.ModuleEntry;
import org.apache.ivy.core.search.OrganisationEntry;
import org.apache.ivy.core.search.RevisionEntry;
import org.apache.ivy.plugins.latest.LatestStrategy;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.resolver.util.MDResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.MetaDataOnlyExternalResource;
import org.gradle.api.internal.externalresource.MissingExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class ExternalResourceResolver implements DependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2compatible;
    private boolean checkconsistency = true;
    private boolean allownomd = true;
    private boolean force;
    private String checksums;
    private String name;
    private ResolverSettings settings;
    private LatestStrategy latestStrategy;
    private String latestStrategyName;
    private String cacheManagerName;
    private RepositoryCacheManager repositoryCacheManager;
    private String changingMatcherName;
    private String changingPattern;
    private Boolean checkmodified;

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    protected VersionLister versionLister;
    private ArtifactResourceResolver artifactResourceResolver = new ArtifactResourceResolver() {
        public ResolvedResource resolve(Artifact artifact) {
            return getArtifactRef(artifact, null);
        }
    };
    private final ResourceDownloader resourceDownloader = new ResourceDownloader() {
        public void download(Artifact artifact, Resource resource, File dest) throws IOException {
            getAndCheck(resource, dest);
        }
    };

    public ExternalResourceResolver(String name,
                                    ExternalResourceRepository repository,
                                    VersionLister versionLister,
                                    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder
    ) {
        this.name = name;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return getName();
    }

    public void setSettings(ResolverSettings ivy) {
        this.settings = ivy;
    }

    public ResolverSettings getSettings() {
        return settings;
    }

    protected ExternalResourceRepository getRepository() {
        return repository;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        // This is not used
        throw new UnsupportedOperationException();
    }

    public void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaData result) {
        ModuleRevisionId nsMrid = dependencyDescriptor.getDependencyRevisionId();

        boolean isDynamic = getVersionMatcher().isDynamic(nsMrid);

        ResolvedResource ivyRef = findIvyFileRef(dependencyDescriptor);

        // get module descriptor
        ModuleDescriptor nsMd;
        if (ivyRef == null) {
            if (!isAllownomd()) {
                LOGGER.debug("No ivy file found for module '{}' in repository '{}'.", nsMrid, getName());
                result.missing();
                return;
            }
            nsMd = DefaultModuleDescriptor.newDefaultInstance(nsMrid, dependencyDescriptor.getAllDependencyArtifacts());
            ResolvedResource artifactRef = findFirstArtifactRef(nsMd);
            if (artifactRef == null) {
                LOGGER.debug("No ivy file nor artifact found for module '{}' in repository '{}'.", nsMrid, getName());
                result.missing();
            } else {
                long lastModified = artifactRef.getLastModified();
                if (lastModified != 0 && nsMd instanceof DefaultModuleDescriptor) {
                    ((DefaultModuleDescriptor) nsMd).setLastModified(lastModified);
                }
                LOGGER.debug("No ivy file found for module '{}' in repository '{}', using default data instead.", nsMrid, getName());
                if (isDynamic) {
                    nsMd.setResolvedModuleRevisionId(ModuleRevisionId.newInstance(nsMrid, artifactRef.getRevision()));
                }
                result.resolved(nsMd, isChanging(nsMd), null);
            }
        } else {
            try {
                ResolvedModuleRevision rmr = null;
                if (ivyRef instanceof MDResolvedResource) {
                    rmr = ((MDResolvedResource) ivyRef).getResolvedModuleRevision();
                }
                if (rmr == null) {
                    rmr = parse(ivyRef, dependencyDescriptor);
                }

                nsMd = rmr.getDescriptor();

                // check descriptor data is in sync with resource revision and names
                if (isCheckconsistency()) {
                    checkDescriptorConsistency(nsMrid, nsMd, ivyRef);
                }
                LOGGER.debug("Ivy file found for module '{}' in repository '{}'.", nsMrid, getName());
                result.resolved(nsMd, isChanging(nsMd), null);
            } catch (ParseException e) {
                result.failed(new ModuleVersionResolveException(nsMrid, e));
            }
        }
    }

    protected VersionMatcher getVersionMatcher() {
        return getSettings().getVersionMatcher();
    }

    private ResolvedModuleRevision parse(final ResolvedResource mdRef, DependencyDescriptor dd) throws ParseException {
        //TODO: check why we don't use our own ParserRegistry here.
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        ModuleDescriptorParser parser = ModuleDescriptorParserRegistry.getInstance().getParser(mdRef.getResource());
        if (parser == null) {
            throw new RuntimeException("no module descriptor parser available for " + mdRef.getResource());
        }

        ModuleRevisionId resolvedMrid = mrid;

        // first check if this dependency has not yet been resolved
        if (getVersionMatcher().isDynamic(mrid)) {
            resolvedMrid = ModuleRevisionId.newInstance(mrid, mdRef.getRevision());
        }

        Artifact moduleArtifact = parser.getMetadataArtifact(resolvedMrid, mdRef.getResource());
        return getRepositoryCacheManager().cacheModuleDescriptor(this, mdRef, dd, moduleArtifact, resourceDownloader, new CacheMetadataOptions());
    }

    private void checkDescriptorConsistency(ModuleRevisionId mrid, ModuleDescriptor md,
                                            ResolvedResource ivyRef) throws ParseException {
        boolean ok = true;
        StringBuilder errors = new StringBuilder();
        if (!mrid.getOrganisation().equals(md.getModuleRevisionId().getOrganisation())) {
            Message.error("\t" + getName() + ": bad organisation found in " + ivyRef.getResource()
                    + ": expected='" + mrid.getOrganisation() + "' found='"
                    + md.getModuleRevisionId().getOrganisation() + "'");
            errors.append("bad organisation: expected='" + mrid.getOrganisation() + "' found='"
                    + md.getModuleRevisionId().getOrganisation() + "'; ");
            ok = false;
        }
        if (!mrid.getName().equals(md.getModuleRevisionId().getName())) {
            Message.error("\t" + getName() + ": bad module name found in " + ivyRef.getResource()
                    + ": expected='" + mrid.getName() + " found='"
                    + md.getModuleRevisionId().getName() + "'");
            errors.append("bad module name: expected='" + mrid.getName() + "' found='"
                    + md.getModuleRevisionId().getName() + "'; ");
            ok = false;
        }
        if (mrid.getBranch() != null
                && !mrid.getBranch().equals(md.getModuleRevisionId().getBranch())) {
            Message.error("\t" + getName() + ": bad branch name found in " + ivyRef.getResource()
                    + ": expected='" + mrid.getBranch() + " found='"
                    + md.getModuleRevisionId().getBranch() + "'");
            errors.append("bad branch name: expected='" + mrid.getBranch() + "' found='"
                    + md.getModuleRevisionId().getBranch() + "'; ");
            ok = false;
        }
        if (ivyRef.getRevision() != null && !ivyRef.getRevision().startsWith("working@")) {
            ModuleRevisionId expectedMrid = ModuleRevisionId
                    .newInstance(mrid, ivyRef.getRevision());
            if (!getVersionMatcher().accept(expectedMrid, md)) {
                Message.error("\t" + getName() + ": bad revision found in " + ivyRef.getResource()
                        + ": expected='" + ivyRef.getRevision() + " found='"
                        + md.getModuleRevisionId().getRevision() + "'");
                errors.append("bad revision: expected='" + ivyRef.getRevision() + "' found='"
                        + md.getModuleRevisionId().getRevision() + "'; ");
                ok = false;
            }
        }
        if (!getSettings().getStatusManager().isStatus(md.getStatus())) {
            Message.error("\t" + getName() + ": bad status found in " + ivyRef.getResource()
                    + ": '" + md.getStatus() + "'");
            errors.append("bad status: '" + md.getStatus() + "'; ");
            ok = false;
        }
        if (!ok) {
            throw new ParseException("inconsistent module descriptor file found in '"
                    + ivyRef.getResource() + "': " + errors, 0);
        }
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        // This is not used
        throw new UnsupportedOperationException();
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        Artifact artifact = DefaultArtifact.newIvyArtifact(mrid, null);
        return findResourceUsingPatterns(mrid, ivyPatterns, artifact, getRMDParser(dd), null, true);
    }

    protected ResolvedResource findFirstArtifactRef(ModuleDescriptor md) {
        for (String configuration : md.getConfigurationsNames()) {
            for (Artifact artifact : md.getArtifacts(configuration)) {
                ResolvedResource artifactRef = getArtifactRef(artifact, null, false);
                if (artifactRef != null) {
                    return artifactRef;
                }
            }
        }
        return null;
    }

    public boolean exists(Artifact artifact) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    public ArtifactOrigin locate(Artifact artifact) {
        ResolvedResource artifactRef = getArtifactRef(artifact, null, false);
        if (artifactRef != null && artifactRef.getResource().exists()) {
            final Resource resource = artifactRef.getResource();
            return new ArtifactOrigin(artifact, resource.isLocal(), resource.getName());
        }
        return null;
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date) {
        return getArtifactRef(artifact, date, true);
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        ResourceMDParser parser = getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId());
        return findResourceUsingPatterns(moduleRevisionId, getArtifactPatterns(), artifact, parser, date, forDownload);
    }

    protected ResourceMDParser getRMDParser(final DependencyDescriptor dd) {
        return new ResourceMDParser() {
            public MDResolvedResource parse(Resource resource, String rev) {
                try {
                    ResolvedModuleRevision rmr = ExternalResourceResolver.this.parse(new ResolvedResource(resource, rev), dd);
                    if (rmr == null) {
                        return null;
                    } else {
                        return new MDResolvedResource(resource, rev, rmr);
                    }
                } catch (ParseException e) {
                    Message.warn("Failed to parse the file '" + resource + "': "
                            + e.getMessage());
                    return null;
                }
            }

        };
    }

    protected ResourceMDParser getDefaultRMDParser(final ModuleId mid) {
        return new ResourceMDParser() {
            public MDResolvedResource parse(Resource resource, String rev) {
                DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(new ModuleRevisionId(mid, rev));
                md.setStatus("integration");
                MetadataArtifactDownloadReport madr = new MetadataArtifactDownloadReport(md.getMetadataArtifact());
                madr.setDownloadStatus(DownloadStatus.NO);
                madr.setSearched(true);
                return new MDResolvedResource(resource, rev, new ResolvedModuleRevision(ExternalResourceResolver.this, ExternalResourceResolver.this, md, madr, isForce()));
            }
        };
    }

    protected ResolvedResource findResourceUsingPatterns(ModuleRevisionId moduleRevision, List<String> patternList, Artifact artifact, ResourceMDParser rmdparser, Date date, boolean forDownload) {
        List<ResolvedResource> resolvedResources = new ArrayList<ResolvedResource>();
        Set<String> foundRevisions = new HashSet<String>();
        boolean dynamic = getVersionMatcher().isDynamic(moduleRevision);
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            ResolvedResource rres = findResourceUsingPattern(moduleRevision, resourcePattern, artifact, rmdparser, date, forDownload);
            if ((rres != null) && !foundRevisions.contains(rres.getRevision())) {
                // only add the first found ResolvedResource for each revision
                foundRevisions.add(rres.getRevision());
                resolvedResources.add(rres);
                if (!dynamic) {
                    break;
                }
            }
        }

        if (resolvedResources.size() > 1) {
            ResolvedResource[] rress = resolvedResources.toArray(new ResolvedResource[resolvedResources.size()]);
            List<ResolvedResource> sortedResources = getLatestStrategy().sort(rress);
            // Discard all but the last, which is returned
            for (int i = 0; i < sortedResources.size() - 1; i++) {
                ResolvedResource resolvedResource = sortedResources.get(i);
                discardResource(resolvedResource.getResource());
            }
            return sortedResources.get(sortedResources.size() - 1);
        } else if (resolvedResources.size() == 1) {
            return resolvedResources.get(0);
        } else {
            return null;
        }
    }

    public ResolvedResource findLatestResource(ModuleRevisionId mrid, VersionList versions, ResourceMDParser rmdparser, Date date, ResourcePattern pattern, Artifact artifact, boolean forDownload) {
        String name = getName();
        VersionMatcher versionMatcher = getVersionMatcher();
        List<String> sorted = versions.sortLatestFirst(getLatestStrategy());
        for (String version : sorted) {
            ModuleRevisionId foundMrid = ModuleRevisionId.newInstance(mrid, version);

            if (!versionMatcher.accept(mrid, foundMrid)) {
                LOGGER.debug(name + ": rejected by version matcher: " + version);
                continue;
            }

            boolean needsModuleDescriptor = versionMatcher.needModuleDescriptor(mrid, foundMrid);
            artifact = DefaultArtifact.cloneWithAnotherMrid(artifact, foundMrid);
            String resourcePath = pattern.toPath(artifact);
            Resource resource = getResource(resourcePath, artifact, forDownload || needsModuleDescriptor);
            String description = version + " [" + resource + "]";
            if (!resource.exists()) {
                LOGGER.debug(name + ": unreachable: " + description);
                discardResource(resource);
                continue;
            }
            if (date != null && resource.getLastModified() > date.getTime()) {
                LOGGER.debug(name + ": too young: " + description);
                discardResource(resource);
                continue;
            }
            if (versionMatcher.needModuleDescriptor(mrid, foundMrid)) {
                MDResolvedResource parsedResource = rmdparser.parse(resource, version);
                if (parsedResource == null) {
                    LOGGER.debug(name + ": impossible to get module descriptor resource: " + description);
                    discardResource(resource);
                    continue;
                }
                ModuleDescriptor md = parsedResource.getResolvedModuleRevision().getDescriptor();
                if (!versionMatcher.accept(mrid, md)) {
                    LOGGER.debug(name + ": md rejected by version matcher: " + description);
                    discardResource(resource);
                    continue;
                }

                return parsedResource;
            }
            return new ResolvedResource(resource, version);
        }
        return null;
    }

    protected ResolvedResource findResourceUsingPattern(ModuleRevisionId moduleRevisionId, ResourcePattern pattern, Artifact artifact, ResourceMDParser resourceParser, Date date, boolean forDownload) {
        VersionMatcher versionMatcher = getVersionMatcher();
        if (!versionMatcher.isDynamic(moduleRevisionId)) {
            return findStaticResourceUsingPattern(moduleRevisionId, pattern, artifact, forDownload);
        } else {
            return findDynamicResourceUsingPattern(resourceParser, moduleRevisionId, pattern, artifact, date, forDownload);
        }
    }

    private ResolvedResource findStaticResourceUsingPattern(ModuleRevisionId moduleRevisionId, ResourcePattern pattern, Artifact artifact, boolean forDownload) {
        String resourceName = pattern.toPath(artifact);
        LOGGER.debug("Loading {}", resourceName);
        Resource res = getResource(resourceName, artifact, forDownload);
        if (res.exists()) {
            String revision = moduleRevisionId.getRevision();
            return new ResolvedResource(res, revision);
        } else {
            LOGGER.debug("Resource not reachable for {}: res={}", moduleRevisionId, res);
            return null;
        }
    }

    private ResolvedResource findDynamicResourceUsingPattern(ResourceMDParser resourceParser, ModuleRevisionId moduleRevisionId, ResourcePattern pattern, Artifact artifact, Date date, boolean forDownload) {
        VersionList versions = listVersions(moduleRevisionId, pattern, artifact);
        ResolvedResource found = findLatestResource(moduleRevisionId, versions, resourceParser, date, pattern, artifact, forDownload);
        if (found == null) {
            LOGGER.debug("No resource found for {}: pattern={}", moduleRevisionId, pattern);
        }
        return found;
    }

    protected void discardResource(Resource resource) {
        if (resource instanceof ExternalResource) {
            try {
                ((ExternalResource) resource).close();
            } catch (IOException e) {
                LOGGER.warn("Exception closing resource " + resource.getName(), e);
            }
        }
    }

    public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        EnhancedArtifactDownloadReport artifactDownloadReport = download(artifact, moduleSource);
        if (downloadFailed(artifactDownloadReport)) {
            result.failed(new ArtifactResolveException(artifactDownloadReport.getArtifact(), artifactDownloadReport.getFailure()));
            return;
        }
        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact);
        }
    }

    protected EnhancedArtifactDownloadReport download(Artifact artifact, ModuleSource moduleSource) {
        return download(artifact);
    }

    public EnhancedArtifactDownloadReport download(Artifact artifact) {
        RepositoryCacheManager cacheManager = getRepositoryCacheManager();

        return (EnhancedArtifactDownloadReport) cacheManager.download(artifact, artifactResourceResolver, resourceDownloader, new CacheDownloadOptions());
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
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
    }

    public void commitPublishTransaction() throws IOException {
    }

    public Namespace getNamespace() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    protected Resource getResource(String source, Artifact target, boolean forDownload) {
        try {
            if (forDownload) {
                ArtifactRevisionId arid = target.getId();
                LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(arid);
                ExternalResource resource = repository.getResource(source, localCandidates);
                return resource == null ? new MissingExternalResource(source) : resource;
            } else {
                // TODO - there's a potential problem here in that we don't carry correct isLocal data in MetaDataOnlyExternalResource
                ExternalResourceMetaData metaData = repository.getResourceMetaData(source);
                return metaData == null ? new MissingExternalResource(source) : new MetaDataOnlyExternalResource(source, metaData);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not get resource '%s'.", source), e);
        }
    }

    protected VersionList listVersions(ModuleRevisionId moduleRevisionId, ResourcePattern pattern, Artifact artifact) {
        try {
            VersionList versionList = versionLister.getVersionList(moduleRevisionId);
            versionList.visit(pattern, artifact);
            return versionList;
        } catch (ResourceNotFoundException e) {
            LOGGER.debug(String.format("Unable to load version list for %s from %s", moduleRevisionId.getModuleId(), getRepository()));
            return new DefaultVersionList(Collections.<String>emptyList());
        }
    }

    protected long get(Resource resource, File destination) throws IOException {
        LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
        if (destination.getParentFile() != null) {
            GFileUtils.mkdirs(destination.getParentFile());
        }

        if (!(resource instanceof ExternalResource)) {
            throw new IllegalArgumentException("Can only download ExternalResource");
        }

        ExternalResource externalResource = (ExternalResource) resource;
        try {
            externalResource.writeTo(destination);
        } finally {
            externalResource.close();
        }
        return destination.length();
    }

    private long getAndCheck(Resource resource, File dest) throws IOException {
        long size = get(resource, dest);
        String[] checksums = getChecksumAlgorithms();
        boolean checked = false;
        for (int i = 0; i < checksums.length && !checked; i++) {
            checked = check(resource, dest, checksums[i]);
        }
        return size;
    }

    private boolean check(Resource resource, File dest, String algorithm) throws IOException {
        if (!ChecksumHelper.isKnownAlgorithm(algorithm)) {
            throw new IllegalArgumentException("Unknown checksum algorithm: " + algorithm);
        }

        ExternalResource checksumResource = repository.getResource(resource.getName() + "." + algorithm);
        if (checksumResource != null && checksumResource.exists()) {
            LOGGER.debug(algorithm + " file found for " + resource + ": checking...");
            File csFile = File.createTempFile("ivytmp", algorithm);
            try {
                get(checksumResource, csFile);
                ChecksumHelper.check(dest, csFile, algorithm);
                Message.verbose(algorithm + " OK for " + resource);
                return true;
            } finally {
                csFile.delete();
            }
        } else {
            return false;
        }
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        String destinationPattern;
        if ("ivy".equals(artifact.getType()) && !getIvyPatterns().isEmpty()) {
            destinationPattern = getIvyPatterns().get(0);
        } else if (!getArtifactPatterns().isEmpty()) {
            destinationPattern = getArtifactPatterns().get(0);
        } else {
            throw new IllegalStateException("impossible to publish " + artifact + " using " + this + ": no artifact pattern defined");
        }
        String destination = toResourcePattern(destinationPattern).toPath(artifact);

        put(src, destination);
        LOGGER.info("Published {} to {}", artifact.getName(), destination);
    }

    private void put(File src, String destination) throws IOException {
        String[] checksums = getChecksumAlgorithms();
        if (checksums.length != 0) {
            // Should not be reachable for publishing
            throw new UnsupportedOperationException();
        }

        repository.put(src, destination);
    }

    public void addIvyPattern(String pattern) {
        ivyPatterns.add(pattern);
    }

    public void addArtifactPattern(String pattern) {
        artifactPatterns.add(pattern);
    }

    public List<String> getIvyPatterns() {
        return Collections.unmodifiableList(ivyPatterns);
    }

    public List<String> getArtifactPatterns() {
        return Collections.unmodifiableList(artifactPatterns);
    }

    protected void setIvyPatterns(List<String> patterns) {
        ivyPatterns = patterns;
    }

    protected void setArtifactPatterns(List<String> patterns) {
        artifactPatterns = patterns;
    }

    public void dumpSettings() {
        // this is not used
        throw new UnsupportedOperationException();
    }

    public boolean isM2compatible() {
        return m2compatible;
    }

    public void setM2compatible(boolean compatible) {
        m2compatible = compatible;
    }

    public boolean isCheckconsistency() {
        return checkconsistency;
    }

    public void setCheckconsistency(boolean checkConsistency) {
        checkconsistency = checkConsistency;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isAllownomd() {
        return allownomd;
    }

    public void setAllownomd(boolean b) {
        Message.deprecated(
            "allownomd is deprecated, please use descriptor=\""
            + (b ? BasicResolver.DESCRIPTOR_OPTIONAL : BasicResolver.DESCRIPTOR_REQUIRED) + "\" instead");
        allownomd = b;
    }

    /**
     * Sets the module descriptor presence rule.
     * Should be one of {@link BasicResolver#DESCRIPTOR_REQUIRED} or {@link BasicResolver#DESCRIPTOR_OPTIONAL}.
     *
     * @param descriptorRule the descriptor rule to use with this resolver.
     */
    public void setDescriptor(String descriptorRule) {
        if (BasicResolver.DESCRIPTOR_REQUIRED.equals(descriptorRule)) {
          allownomd = false;
        } else if (BasicResolver.DESCRIPTOR_OPTIONAL.equals(descriptorRule)) {
          allownomd = true;
        } else {
            throw new IllegalArgumentException(
                "unknown descriptor rule '" + descriptorRule
                + "'. Allowed rules are: "
                + Arrays.asList(new String[] {BasicResolver.DESCRIPTOR_REQUIRED, BasicResolver.DESCRIPTOR_OPTIONAL}));
        }
    }

    public String[] getChecksumAlgorithms() {
        if (checksums == null) {
            return new String[0];
        }
        // csDef is a comma separated list of checksum algorithms to use with this resolver
        // we parse and return it as a String[]
        String[] checksums = this.checksums.split(",");
        List<String> algos = new ArrayList<String>();
        for (int i = 0; i < checksums.length; i++) {
            String cs = checksums[i].trim();
            if (!"".equals(cs) && !"none".equals(cs)) {
                algos.add(cs);
            }
        }
        return algos.toArray(new String[algos.size()]);
    }

    public void setChecksums(String checksums) {
        this.checksums = checksums;
    }

    public LatestStrategy getLatestStrategy() {
        if (latestStrategy == null) {
            initLatestStrategyFromSettings();
        }
        return latestStrategy;
    }

    private void initLatestStrategyFromSettings() {
        if (getSettings() != null) {
            if (latestStrategyName != null && !"default".equals(latestStrategyName)) {
                latestStrategy = getSettings().getLatestStrategy(latestStrategyName);
                if (latestStrategy == null) {
                    throw new IllegalStateException(
                        "unknown latest strategy '" + latestStrategyName + "'");
                }
            } else {
                latestStrategy = getSettings().getDefaultLatestStrategy();
                Message.debug(getName() + ": no latest strategy defined: using default");
            }
        } else {
            throw new IllegalStateException(
                "no ivy instance found: "
                + "impossible to get a latest strategy without ivy instance");
        }
    }

    public void setLatestStrategy(LatestStrategy latestStrategy) {
        this.latestStrategy = latestStrategy;
    }

    public void setLatest(String strategyName) {
        latestStrategyName = strategyName;
    }

    public String getLatest() {
        if (latestStrategyName == null) {
            latestStrategyName = "default";
        }
        return latestStrategyName;
    }

    public void setChangingMatcher(String changingMatcherName) {
        this.changingMatcherName = changingMatcherName;
    }

    public String getChangingMatcherName() {
        return changingMatcherName;
    }

    public void setChangingPattern(String changingPattern) {
        this.changingPattern = changingPattern;
    }

    public String getChangingPattern() {
        return changingPattern;
    }

    public void setCheckmodified(boolean check) {
        checkmodified = Boolean.valueOf(check);
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return repositoryCacheManager;
    }

    public void setRepositoryCacheManager(RepositoryCacheManager repositoryCacheManager) {
        this.repositoryCacheManager = repositoryCacheManager;
    }

    public void setCache(String cacheName) {
        cacheManagerName = cacheName;
    }

    protected ResourcePattern toResourcePattern(String pattern) {
        return isM2compatible() ? new M2ResourcePattern(pattern) : new IvyResourcePattern(pattern);
    }

    protected boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    private boolean isChanging(ModuleDescriptor moduleDescriptor) {
        if (changingMatcherName == null || changingPattern == null) {
            return false;
        }
        PatternMatcher matcher = settings.getMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern).matches(moduleDescriptor.getResolvedModuleRevisionId().getRevision());
    }
}
