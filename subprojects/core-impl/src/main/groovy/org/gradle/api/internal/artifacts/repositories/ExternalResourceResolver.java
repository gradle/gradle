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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.util.MDResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolverHelper;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactOriginWithMetaData;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.MetaDataOnlyExternalResource;
import org.gradle.api.internal.externalresource.MissingExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ExternalResourceResolver extends BasicResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private final TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();
    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2compatible;
    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;

    public ExternalResourceResolver(String name,
                                    ExternalResourceRepository repository,
                                    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                    CachedExternalResourceIndex<String> cachedExternalResourceIndex
    ) {
        setName(name);
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
    }

    protected ExternalResourceRepository getRepository() {
        return repository;
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        if (isM2compatible()) {
            mrid = convertM2IdForResourceSearch(mrid);
        }
        return findResourceUsingPatterns(mrid, ivyPatterns, DefaultArtifact.newIvyArtifact(mrid, data.getDate()), getRMDParser(dd, data), data.getDate(), true);
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        throw new UnsupportedOperationException();
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
        return locate(artifact) != null;
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
        if (isM2compatible()) {
            mrid = convertM2IdForResourceSearch(mrid);
        }
        return findResourceUsingPatterns(mrid, artifactPatterns, artifact,
                getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date, forDownload);
    }

    protected ResolvedResource findResourceUsingPatterns(ModuleRevisionId moduleRevision, List<String> patternList, Artifact artifact, ResourceMDParser rmdparser, Date date, boolean forDownload) {
        List<ResolvedResource> resolvedResources = new ArrayList<ResolvedResource>();
        Set<String> foundRevisions = new HashSet<String>();
        boolean dynamic = getSettings().getVersionMatcher().isDynamic(moduleRevision);
        for (String pattern : patternList) {
            ResolvedResource rres = findResourceUsingPattern(moduleRevision, pattern, artifact, rmdparser, date, forDownload);
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

    public ResolvedResource findLatestResource(ModuleRevisionId mrid, String[] versions, ResourceMDParser rmdparser, Date date, String pattern, Artifact artifact, boolean forDownload) throws IOException {
        String name = getName();
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();

        List<String> sorted = sortVersionsLatestFirst(versions);
        for (String version : sorted) {
            ModuleRevisionId foundMrid = ModuleRevisionId.newInstance(mrid, version);

            if (!versionMatcher.accept(mrid, foundMrid)) {
                LOGGER.debug(name + ": rejected by version matcher: " + version);
                continue;
            }

            boolean needsModuleDescriptor = versionMatcher.needModuleDescriptor(mrid, foundMrid);
            String resourcePath = IvyPatternHelper.substitute(pattern, foundMrid, artifact);
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
                if (md.isDefault()) {
                    LOGGER.debug(name + ": default md rejected by version matcher requiring module descriptor: " + description);
                    discardResource(resource);
                    continue;
                } else if (!versionMatcher.accept(mrid, md)) {
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

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        EventManager eventManager = getEventManager();
        try {
            if (eventManager != null) {
                repository.addTransferListener(eventManager);
            }
            return super.download(artifacts, options);
        } finally {
            if (eventManager != null) {
                repository.removeTransferListener(eventManager);
            }
        }
    }

    protected ResolvedResource findResourceUsingPattern(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact, ResourceMDParser resourceParser, Date date, boolean forDownload) {
        String name = getName();
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();
        try {
            if (!versionMatcher.isDynamic(moduleRevisionId)) {
                return findStaticResourceUsingPattern(moduleRevisionId, pattern, artifact, forDownload);
            } else {
                return findDynamicResourceUsingPattern(resourceParser, moduleRevisionId, pattern, artifact, date, forDownload);
            }
        } catch (IOException ex) {
            throw new RuntimeException(name + ": unable to get resource for " + moduleRevisionId + ": res=" + IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact) + ": " + ex, ex);
        }
    }

    private ResolvedResource findStaticResourceUsingPattern(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact, boolean forDownload) throws IOException {
        String resourceName = IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact);
        logAttempt(resourceName);

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

    private ResolvedResource findDynamicResourceUsingPattern(ResourceMDParser resourceParser, ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact, Date date, boolean forDownload) throws IOException {
        logAttempt(IvyPatternHelper.substitute(pattern, ModuleRevisionId.newInstance(moduleRevisionId, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)), artifact));
        String[] versions = listVersions(moduleRevisionId, pattern, artifact);
        if (versions == null) {
            LOGGER.debug("Unable to list versions for {}: pattern={}", moduleRevisionId, pattern);
            return null;
        } else {
            ResolvedResource found = findLatestResource(moduleRevisionId, versions, resourceParser, date, pattern, artifact, forDownload);
            if (found == null) {
                LOGGER.debug("No resource found for {}: pattern={}", moduleRevisionId, pattern);
            }
            return found;
        }
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

    private List<String> sortVersionsLatestFirst(String[] versions) {
        ArtifactInfo[] artifactInfos = new ArtifactInfo[versions.length];
        for (int i = 0; i < versions.length; i++) {
            String version = versions[i];
            artifactInfos[i] = new VersionArtifactInfo(version);
        }
        List<ArtifactInfo> sorted = getLatestStrategy().sort(artifactInfos);
        Collections.reverse(sorted);

        List<String> sortedVersions = new ArrayList<String>();
        for (ArtifactInfo info : sorted) {
            sortedVersions.add(info.getRevision());
        }
        return sortedVersions;
    }

    protected Resource getResource(String source) throws IOException {
        ExternalResource resource = repository.getResource(source);
        return resource == null ? new MissingExternalResource(source) : resource;
    }

    protected Resource getResource(String source, Artifact target, boolean forDownload) throws IOException {
        if (forDownload) {
            ArtifactRevisionId arid = target.getId();
            LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(arid);
            CachedExternalResource cached = cachedExternalResourceIndex.lookup(source);
            ExternalResource resource = repository.getResource(source, localCandidates, cached);
            return resource == null ? new MissingExternalResource(source) : resource;
        } else {
            // TODO - there's a potential problem here in that we don't carry correct isLocal data in MetaDataOnlyExternalResource
            ExternalResourceMetaData metaData = repository.getResourceMetaData(source);
            return metaData == null ? new MissingExternalResource(source) : new MetaDataOnlyExternalResource(source, metaData);
        }
    }

    protected String[] listVersions(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) {
        ModuleRevisionId idWithoutRevision = ModuleRevisionId.newInstance(moduleRevisionId, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY));
        String partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, idWithoutRevision, artifact);
        LOGGER.debug("Listing all in {}", partiallyResolvedPattern);
        return ResolverHelper.listTokenValues(repository, partiallyResolvedPattern, IvyPatternHelper.REVISION_KEY);
    }

    protected long get(Resource resource, File destination) throws IOException {
        LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }

        if (!(resource instanceof ExternalResource)) {
            throw new IllegalArgumentException("Can only download ExternalResource");
        }

        repository.downloadResource((ExternalResource) resource, destination);
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
        // Check for m2 compatibility
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        if (isM2compatible()) {
            moduleRevisionId = convertM2IdForResourceSearch(moduleRevisionId);
        }

        String destination = getDestination(destinationPattern, artifact, moduleRevisionId);

        put(artifact, src, destination, overwrite);
        LOGGER.info("Published {} to {}", artifact.getName(), hidePassword(destination));
    }

    private String getDestination(String pattern, Artifact artifact, ModuleRevisionId moduleRevisionId) {
        return IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact);
    }

    private void put(Artifact artifact, File src, String destination, boolean overwrite) throws IOException {
        // verify the checksum algorithms before uploading artifacts!
        String[] checksums = getChecksumAlgorithms();
        for (String checksum : checksums) {
            if (!ChecksumHelper.isKnownAlgorithm(checksum)) {
                throw new IllegalArgumentException("Unknown checksum algorithm: " + checksum);
            }
        }

        repository.put(artifact, src, destination, overwrite);
        for (String checksum : checksums) {
            putChecksum(artifact, src, destination, overwrite, checksum);
        }
    }

    private void putChecksum(Artifact artifact, File src, String destination, boolean overwrite,
                             String algorithm) throws IOException {
        File csFile = temporaryFileProvider.createTemporaryFile("ivytemp", algorithm);
        try {
            FileUtil.copy(new ByteArrayInputStream(ChecksumHelper.computeAsString(src, algorithm)
                    .getBytes()), csFile, null);
            repository.put(DefaultArtifact.cloneWithAnotherTypeAndExt(artifact, algorithm,
                    artifact.getExt() + "." + algorithm), csFile, destination + "." + algorithm, overwrite);
        } finally {
            csFile.delete();
        }
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

    protected void setIvyPatterns(List patterns) {
        ivyPatterns = patterns;
    }

    protected void setArtifactPatterns(List patterns) {
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

    protected ModuleRevisionId convertM2IdForResourceSearch(ModuleRevisionId mrid) {
        if (mrid.getOrganisation() == null || mrid.getOrganisation().indexOf('.') == -1) {
            return mrid;
        }
        return ModuleRevisionId.newInstance(mrid.getOrganisation().replace('.', '/'),
                mrid.getName(), mrid.getBranch(), mrid.getRevision(),
                mrid.getQualifiedExtraAttributes());
    }

    private class VersionArtifactInfo implements ArtifactInfo {
        private final String version;

        private VersionArtifactInfo(String version) {
            this.version = version;
        }

        public String getRevision() {
            return version;
        }

        public long getLastModified() {
            return 0;
        }
    }
}
