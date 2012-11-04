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
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.util.MDResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactOriginWithMetaData;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.MetaDataOnlyExternalResource;
import org.gradle.api.internal.externalresource.MissingExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class ExternalResourceResolver extends BasicResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2compatible;
    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    protected VersionLister versionLister;
    private ArtifactResourceResolver artifactResourceResolver = new ArtifactResourceResolver() {
        public ResolvedResource resolve(Artifact artifact) {
            return getArtifactRef(toSystem(artifact), null);
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
        setName(name);
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
    }

    protected ExternalResourceRepository getRepository() {
        return repository;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data)
            throws ParseException {
        DependencyDescriptor systemDd = dd;
        DependencyDescriptor nsDd = fromSystem(dd);

        ModuleRevisionId systemMrid = systemDd.getDependencyRevisionId();
        ModuleRevisionId nsMrid = nsDd.getDependencyRevisionId();

        boolean isDynamic = getSettings().getVersionMatcher().isDynamic(systemMrid);
        ResolvedModuleRevision rmr = null;

        ResolvedResource ivyRef = findIvyFileRef(nsDd, data);

        // get module descriptor
        ModuleDescriptor nsMd;
        ModuleDescriptor systemMd;
        if (ivyRef == null) {
            if (!isAllownomd()) {
                LOGGER.debug("No ivy file found for {}", systemMrid);
                return null;
            }
            nsMd = DefaultModuleDescriptor.newDefaultInstance(nsMrid, nsDd
                    .getAllDependencyArtifacts());
            ResolvedResource artifactRef = findFirstArtifactRef(nsMd, nsDd, data);
            if (artifactRef == null) {
                LOGGER.debug("No ivy file nor artifact found for {}", systemMrid);
                return null;
            } else {
                long lastModified = artifactRef.getLastModified();
                if (lastModified != 0 && nsMd instanceof DefaultModuleDescriptor) {
                    ((DefaultModuleDescriptor) nsMd).setLastModified(lastModified);
                }
                Message.verbose("\t" + getName() + ": no ivy file found for " + systemMrid
                        + ": using default data");
                if (isDynamic) {
                    nsMd.setResolvedModuleRevisionId(ModuleRevisionId.newInstance(nsMrid,
                            artifactRef.getRevision()));
                }
                systemMd = toSystem(nsMd);
                MetadataArtifactDownloadReport madr =
                        new MetadataArtifactDownloadReport(systemMd.getMetadataArtifact());
                madr.setDownloadStatus(DownloadStatus.NO);
                madr.setSearched(true);
                rmr = new ResolvedModuleRevision(this, this, systemMd, madr, isForce());
            }
        } else {
            if (ivyRef instanceof MDResolvedResource) {
                rmr = ((MDResolvedResource) ivyRef).getResolvedModuleRevision();
            }
            if (rmr == null) {
                rmr = parse(ivyRef, systemDd, data);
            }
            if (!rmr.getReport().isDownloaded()
                    && rmr.getReport().getLocalFile() != null) {
                return rmr;
            } else {
                nsMd = rmr.getDescriptor();

                // check descriptor data is in sync with resource revision and names
                systemMd = toSystem(nsMd);
                if (isCheckconsistency()) {
                    checkDescriptorConsistency(systemMrid, systemMd, ivyRef);
                    checkDescriptorConsistency(nsMrid, nsMd, ivyRef);
                }
                rmr = new ResolvedModuleRevision(
                        this, this, systemMd, toSystem(rmr.getReport()), isForce());
            }
        }

        return rmr;
    }

    public ResolvedModuleRevision parse(final ResolvedResource mdRef, DependencyDescriptor dd,
            ResolveData data) throws ParseException {

        DependencyDescriptor nsDd = dd;
        dd = toSystem(nsDd);

        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        ModuleDescriptorParser parser = ModuleDescriptorParserRegistry
                .getInstance().getParser(mdRef.getResource());
        if (parser == null) {
            throw new RuntimeException("no module descriptor parser available for " + mdRef.getResource());
        }

        ModuleRevisionId resolvedMrid = mrid;

        // first check if this dependency has not yet been resolved
        if (getSettings().getVersionMatcher().isDynamic(mrid)) {
            resolvedMrid = ModuleRevisionId.newInstance(mrid, mdRef.getRevision());
        }

        Artifact moduleArtifact = parser.getMetadataArtifact(resolvedMrid, mdRef.getResource());
        return getRepositoryCacheManager().cacheModuleDescriptor(this, mdRef, dd, moduleArtifact, resourceDownloader, getCacheOptions(data));
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
            if (!getSettings().getVersionMatcher().accept(expectedMrid, md)) {
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
        for (Iterator it = mrid.getExtraAttributes().entrySet().iterator(); it.hasNext();) {
            Map.Entry extra = (Map.Entry) it.next();
            if (extra.getValue() != null && !extra.getValue().equals(
                                                md.getExtraAttribute((String) extra.getKey()))) {
                String errorMsg = "bad " + extra.getKey() + " found in " + ivyRef.getResource()
                                        + ": expected='" + extra.getValue() + "' found='"
                                        + md.getExtraAttribute((String) extra.getKey()) + "'";
                Message.error("\t" + getName() + ": " + errorMsg);
                errors.append(errorMsg + ";");
                ok = false;
            }
        }
        if (!ok) {
            throw new ParseException("inconsistent module descriptor file found in '"
                    + ivyRef.getResource() + "': " + errors, 0);
        }
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        return findResourceUsingPatterns(mrid, ivyPatterns, DefaultArtifact.newIvyArtifact(mrid, data.getDate()), getRMDParser(dd, data), data.getDate(), true);
    }

    @Override
    protected ResolvedResource findFirstArtifactRef(ModuleDescriptor md, DependencyDescriptor dd,
                                                    ResolveData data) {
        for (String configuration : md.getConfigurationsNames()) {
            for (Artifact artifact : md.getArtifacts(configuration)) {
                ResolvedResource artifactRef = getArtifactRef(artifact, data.getDate(), false);
                if (artifactRef != null) {
                    return artifactRef;
                }
            }
        }
        return null;
    }

    @Override
    public boolean exists(Artifact artifact) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactOrigin locate(Artifact artifact) {
        ResolvedResource artifactRef = getArtifactRef(artifact, null, false);
        if (artifactRef != null && artifactRef.getResource().exists()) {
            return new ArtifactOriginWithMetaData(artifact, artifactRef.getResource());
        }
        return null;
    }

    @Override
    protected ResolvedResource getArtifactRef(Artifact artifact, Date date) {
        return getArtifactRef(artifact, date, true);
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId mrid = artifact.getModuleRevisionId();
        return findResourceUsingPatterns(mrid, artifactPatterns, artifact,
                getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date, forDownload);
    }

    protected ResourceMDParser getDefaultRMDParser(final ModuleId mid) {
        return new ResourceMDParser() {
            public MDResolvedResource parse(Resource resource, String rev) {
                DefaultModuleDescriptor md =
                    DefaultModuleDescriptor.newDefaultInstance(new ModuleRevisionId(mid, rev));
                md.setStatus("integration");
                MetadataArtifactDownloadReport madr =
                    new MetadataArtifactDownloadReport(md.getMetadataArtifact());
                madr.setDownloadStatus(DownloadStatus.NO);
                madr.setSearched(true);
                return new MDResolvedResource(resource, rev, new ResolvedModuleRevision(ExternalResourceResolver.this, ExternalResourceResolver.this, md, madr, isForce()));
            }
        };
    }

    protected ResolvedResource findResourceUsingPatterns(ModuleRevisionId moduleRevision, List<String> patternList, Artifact artifact, ResourceMDParser rmdparser, Date date, boolean forDownload) {
        List<ResolvedResource> resolvedResources = new ArrayList<ResolvedResource>();
        Set<String> foundRevisions = new HashSet<String>();
        boolean dynamic = getSettings().getVersionMatcher().isDynamic(moduleRevision);
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
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();
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
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();
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

    public EnhancedArtifactDownloadReport download(Artifact artifact) {
        RepositoryCacheManager cacheManager = getRepositoryCacheManager();
        return (EnhancedArtifactDownloadReport) cacheManager.download(artifact, artifactResourceResolver, resourceDownloader, new CacheDownloadOptions());
    }

    @Override
    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactDownloadReport download(ArtifactOrigin origin, DownloadOptions options) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public void reportFailure() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public void reportFailure(Artifact art) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] listTokenValues(String token, Map otherTokenValues) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public Map[] listTokenValues(String[] tokens, Map criteria) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public OrganisationEntry[] listOrganisations() {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleEntry[] listModules(OrganisationEntry org) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    @Override
    public RevisionEntry[] listRevisions(ModuleEntry mod) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        // This is never used
        throw new UnsupportedOperationException();
    }

    protected Resource getResource(String source) throws IOException {
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
            destination.getParentFile().mkdirs();
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
        LOGGER.info("Published {} to {}", artifact.getName(), hidePassword(destination));
    }

    private void put(File src, String destination) throws IOException {
        String[] checksums = getChecksumAlgorithms();
        if (checksums.length != 0) {
            // Should not be reachable for publishing
            throw new UnsupportedOperationException();
        }

        repository.put(src, destination);
    }

    protected Collection findNames(Map tokenValues, String token) {
        throw new UnsupportedOperationException();
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
        super.dumpSettings();
        Message.debug("\t\tm2compatible: " + isM2compatible());
        Message.debug("\t\tivy patterns:");
        for (String p : getIvyPatterns()) {
            Message.debug("\t\t\t" + p);
        }
        Message.debug("\t\tartifact patterns:");
        for (String p : getArtifactPatterns()) {
            Message.debug("\t\t\t" + p);
        }
        Message.debug("\t\trepository: " + repository);
    }

    public boolean isM2compatible() {
        return m2compatible;
    }

    public void setM2compatible(boolean compatible) {
        m2compatible = compatible;
    }

    protected ResourcePattern toResourcePattern(String pattern) {
        return isM2compatible() ? new M2ResourcePattern(pattern) : new IvyResourcePattern(pattern);
    }
}
