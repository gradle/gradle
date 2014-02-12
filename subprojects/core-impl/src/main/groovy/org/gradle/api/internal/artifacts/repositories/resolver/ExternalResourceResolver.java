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

import com.google.common.base.Joiner;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.MetaDataOnlyExternalResource;
import org.gradle.api.internal.externalresource.MissingExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache.ExternalResourceDownloader;

public abstract class ExternalResourceResolver implements ModuleVersionPublisher, ConfiguredModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private final MetaDataParser metaDataParser;
    private final ModuleMetadataProcessor metadataProcessor;

    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2Compatible;
    private boolean checkConsistency = true;
    private boolean allowMissingDescriptor = true;
    private boolean force;
    private String checksums;
    private String name;
    private RepositoryArtifactCache repositoryCacheManager;
    private String changingMatcherName;
    private String changingPattern;
    private DependencyToModuleVersionResolver nestedResolver;

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    private final LatestStrategy latestStrategy;
    private final ResolverStrategy resolverStrategy;
    private final VersionMatcher versionMatcher;

    protected VersionLister versionLister;

    // TODO:DAZ Get rid of this
    private final ExternalResourceDownloader resourceDownloader = new ExternalResourceDownloader() {
        public void download(ExternalResource resource, File dest) throws IOException {
            get(resource, dest);
            verifyChecksums(resource, dest);
        }
    };

    public ExternalResourceResolver(String name,
                                    ExternalResourceRepository repository,
                                    VersionLister versionLister,
                                    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                    MetaDataParser metaDataParser,
                                    ModuleMetadataProcessor metadataProcessor,
                                    ResolverStrategy resolverStrategy,
                                    VersionMatcher versionMatcher,
                                    LatestStrategy latestStrategy) {
        this.name = name;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.metaDataParser = metaDataParser;
        this.metadataProcessor = metadataProcessor;
        this.resolverStrategy = resolverStrategy;
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
    }

    public String getId() {
        return DependencyResolverIdentifier.forExternalResourceResolver(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDynamicResolveMode() {
        return false;
    }

    public String toString() {
        return String.format("Repository '%s'", getName());
    }

    public void setResolver(DependencyToModuleVersionResolver resolver) {
        this.nestedResolver = resolver;
    }

    protected ExternalResourceRepository getRepository() {
        return repository;
    }

    public boolean isLocal() {
        return repositoryCacheManager.isLocal();
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
        ModuleIdentifier module  = new DefaultModuleIdentifier(dependency.getRequested().getGroup(), dependency.getRequested().getName());
        if (!versionMatcher.isDynamic(dependency.getRequested().getVersion())) {
            throw new IllegalArgumentException("Can only select for a dynamic version");
        }
        VersionList versionList = versionLister.getVersionList(module);
        // List modules based on metadata files
        Artifact metaDataArtifact = getMetaDataArtifactFor(dependencyDescriptor.getDependencyRevisionId());
        listVersionsForAllPatterns(module, getIvyPatterns(), metaDataArtifact, versionList);

        // List modules with missing metadata files
        // TODO:DAZ Should check isAllownomd()
        Artifact[] otherArtifacts = getDefaultMetaData(dependency, dependencyDescriptor.getDependencyRevisionId()).getDescriptor().getAllArtifacts();
        for (Artifact otherArtifact : otherArtifacts) {
            listVersionsForAllPatterns(module, getArtifactPatterns(), otherArtifact, versionList);
        }
        DefaultModuleVersions moduleVersions = new DefaultModuleVersions();
        for (VersionList.ListedVersion listedVersion : versionList.getVersions()) {
            moduleVersions.add(listedVersion.getVersion());
        }
        result.listed(moduleVersions);
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
        ModuleRevisionId moduleRevisionId = dependencyDescriptor.getDependencyRevisionId();
        if (versionMatcher.isDynamic(moduleRevisionId.getRevision())) {
            throw new IllegalArgumentException("Must first select a static version");
        }
        findStaticDependency(dependency, result);
    }

    protected void findStaticDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
        DownloadedAndParsedMetaDataArtifact ivyRef = findMetaDataFileUsingAnyPattern(dependency, dependencyDescriptor.getDependencyRevisionId());

        // get module descriptor
        if (ivyRef == null) {
            getDependencyForMissingIvyFileRef(dependency, result, dependencyDescriptor.getDependencyRevisionId());
        } else {
            getDependencyForFoundIvyFileRef(dependency, result, ivyRef.getArtifact().getModuleRevisionId(), ivyRef);
        }
    }

    private void getDependencyForMissingIvyFileRef(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result, ModuleRevisionId moduleRevisionId) {
        DownloadedAndParsedMetaDataArtifact artifactRef = findDefaultArtifactUsingAnyPattern(dependency, moduleRevisionId);
        if (artifactRef == null) {
            LOGGER.debug("No meta-data file nor artifact found for module '{}' in repository '{}'.", moduleRevisionId, getName());
            result.missing();
        } else {
            result.resolved(artifactRef.moduleVersionMetaData, null);
        }
    }

    protected MutableModuleVersionMetaData getDefaultMetaData(DependencyMetaData dependency, ModuleRevisionId moduleRevisionId) {
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(moduleRevisionId, dependency.getDescriptor().getAllDependencyArtifacts());
        moduleDescriptor.setStatus("integration");
        MutableModuleVersionMetaData rawMetaData = new ModuleDescriptorAdapter(moduleDescriptor);
        return processRawMetaData(rawMetaData);
    }

    protected void getDependencyForFoundIvyFileRef(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result, ModuleRevisionId moduleRevisionId, DownloadedAndParsedMetaDataArtifact metaDataArtifact) {
        MutableModuleVersionMetaData moduleVersionMetaData = metaDataArtifact.moduleVersionMetaData;
        LOGGER.debug("Ivy file found for module '{}' in repository '{}'.", moduleRevisionId, getName());
        result.resolved(moduleVersionMetaData, null);
    }

    protected MutableModuleVersionMetaData getArtifactMetadata(DependencyMetaData dependency, Artifact artifact, ExternalResource resource) {
        MutableModuleVersionMetaData rawMetaData = doGetArtifactMetadata(artifact, resource);
        return processRawMetaData(rawMetaData);
    }

    private MutableModuleVersionMetaData processRawMetaData(MutableModuleVersionMetaData rawMetaData) {
        rawMetaData.setChanging(isChanging(rawMetaData.getDescriptor()));
        MutableModuleVersionMetaData metaData = rawMetaData.copy();
        metaData.setRawMetaData(rawMetaData);
        metadataProcessor.process(metaData);
        return metaData;
    }

    private MutableModuleVersionMetaData doGetArtifactMetadata(Artifact artifact, ExternalResource resource) {
        ModuleRevisionId dependencyRevisionId = artifact.getId().getModuleRevisionId();
        LocallyAvailableExternalResource cachedResource;
        try {
            cachedResource = repositoryCacheManager.downloadAndCacheArtifactFile(artifact.getId(), resourceDownloader, resource);
        } catch (IOException e) {
            // TODO:DAZ Work out if/when/why this happens
            LOGGER.warn("Problem while downloading module descriptor: {}: {}", resource, e.getMessage());
            return null;
        }

        return metaDataParser.parseMetaData(new ExternalResourceResolverDescriptorParseContext(nestedResolver, this, dependencyRevisionId), cachedResource);
    }

    protected void checkMetadataConsistency(ModuleVersionIdentifier expectedId, ModuleVersionMetaData metadata,
                                            ResolvedArtifact ivyRef) throws MetaDataParseException {
        List<String> errors = new ArrayList<String>();
        if (!expectedId.getGroup().equals(metadata.getId().getGroup())) {
            errors.add("bad group: expected='" + expectedId.getGroup() + "' found='" + metadata.getId().getGroup() + "'");
        }
        if (!expectedId.getName().equals(metadata.getId().getName())) {
            errors.add("bad module name: expected='" + expectedId.getName() + "' found='" + metadata.getId().getName() + "'");
        }
        if (!expectedId.getVersion().equals(metadata.getId().getVersion())) {
            errors.add("bad version: expected='" + expectedId.getVersion() + "' found='" + metadata.getId().getVersion() + "'");
        }
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            errors.add("bad status: '" + metadata.getStatus() + "'; ");
        }
        if (errors.size() > 0) {
            throw new MetaDataParseException(String.format("inconsistent module metadata found. Descriptor: %s Errors: %s",
                    ivyRef.resource, Joiner.on(SystemProperties.getLineSeparator()).join(errors)));
        }
    }

    private DownloadedAndParsedMetaDataArtifact findMetaDataFileUsingAnyPattern(DependencyMetaData dependency, ModuleRevisionId moduleRevisionId) {
        Artifact artifact = getMetaDataArtifactFor(moduleRevisionId);
        if (artifact == null) {
            return null;
        }
        ResolvedArtifact metaDataResource = findStaticResourceUsingPatterns(ivyPatterns, artifact, true);
        if (metaDataResource == null) {
            return null;
        }
        MutableModuleVersionMetaData moduleVersionMetaData = getArtifactMetadata(dependency, metaDataResource.getArtifact(), metaDataResource.getResource());

        if (isCheckconsistency()) {
            checkMetadataConsistency(DefaultModuleVersionIdentifier.newId(moduleRevisionId), moduleVersionMetaData, metaDataResource);
        }

        return new DownloadedAndParsedMetaDataArtifact(metaDataResource.resource, metaDataResource.artifact, moduleVersionMetaData);
    }

    @Nullable
    protected abstract Artifact getMetaDataArtifactFor(ModuleRevisionId mrid);

    protected ResolvedArtifact findAnyArtifact(ModuleVersionMetaData metaData) {
        for (Artifact artifact : metaData.getDescriptor().getAllArtifacts()) {
            ResolvedArtifact artifactRef = getArtifactRef(artifact, false);
            if (artifactRef != null) {
                return artifactRef;
            }
        }
        return null;
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        ResolvedArtifact artifactRef = getArtifactRef(artifact.getArtifact(), false);
        return artifactRef != null && artifactRef.resource.exists();
    }

    private ResolvedArtifact getArtifactRef(Artifact artifact, boolean forDownload) {
        return findStaticResourceUsingPatterns(getArtifactPatterns(), artifact, forDownload);
    }

    protected ResolvedArtifact findStaticResourceUsingPatterns(List<String> patternList, Artifact artifact, boolean forDownload) {
        // Static version, return first found
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            String resourceName = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourceName);
            ExternalResource resource = getResource(resourceName, artifact.getId(), forDownload);
            if (resource.exists()) {
                return new ResolvedArtifact(resource, artifact);
            } else {
                LOGGER.debug("Resource not reachable for {}: res={}", artifact, resource);
                discardResource(resource);
            }
        }
        return null;
    }

    private void listVersionsForAllPatterns(ModuleIdentifier module, List<String> patternList, Artifact artifact, VersionList versionList) {
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            try {
                versionList.visit(resourcePattern, artifact);
            } catch (ResourceNotFoundException e) {
                LOGGER.debug(String.format("Unable to load version list for %s from %s", module, getRepository()));
                // Don't add any versions
                // TODO:DAZ Should fail?
            }
        }
    }

    private DownloadedAndParsedMetaDataArtifact findLatestMetaData(DependencyMetaData dependency, VersionList versions) {
        ModuleRevisionId revisionId = dependency.getDescriptor().getDependencyRevisionId();
        String requestedVersion = revisionId.getRevision();
        String name = getName();

        for (VersionList.ListedVersion listedVersion : versions.sortLatestFirst(latestStrategy)) {
            String foundVersion = listedVersion.getVersion();

            boolean needsMetadata = versionMatcher.needModuleMetadata(requestedVersion);
            if (!needsMetadata && !versionMatcher.accept(requestedVersion, foundVersion)) {
                LOGGER.debug(name + ": rejected by version matcher: " + foundVersion);
                continue;
            }

            ModuleRevisionId candidateModuleVersionId = IvyUtil.createModuleRevisionId(revisionId.getOrganisation(), revisionId.getName(), foundVersion, null);
            DownloadedAndParsedMetaDataArtifact resolvedResource = findMetaDataFileUsingAnyPattern(dependency, candidateModuleVersionId);
            if (resolvedResource == null) {
                resolvedResource = findDefaultArtifactUsingAnyPattern(dependency, candidateModuleVersionId);
                if (resolvedResource == null) {
                    continue;
                }
            }
            if (needsMetadata) {
                if (!versionMatcher.accept(requestedVersion, resolvedResource.moduleVersionMetaData)) {
                    LOGGER.debug(name + ": md rejected by version matcher: " + resolvedResource.getResource());
                    discardResource(resolvedResource.getResource());
                    continue;
                }
            }
            return resolvedResource;
        }
        return null;
    }

    private DownloadedAndParsedMetaDataArtifact findDefaultArtifactUsingAnyPattern(DependencyMetaData dependency, ModuleRevisionId moduleRevisionId) {
        if (!isAllownomd()) {
            return null;
        }

        MutableModuleVersionMetaData metaData = getDefaultMetaData(dependency, moduleRevisionId);

        ResolvedArtifact artifactRef = findAnyArtifact(metaData);
        if (artifactRef == null) {
            return null;
        }

        LOGGER.debug("No meta-data file found for module '{}' in repository '{}', using default data instead.", moduleRevisionId, getName());

        return new DownloadedAndParsedMetaDataArtifact(artifactRef.resource, artifactRef.artifact, metaData);
    }

    protected void discardResource(ExternalResource resource) {
        try {
            resource.close();
        } catch (IOException e) {
            LOGGER.warn("Exception closing resource " + resource.getName(), e);
        }
    }

    public void resolve(ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        Artifact ivyArtifact = artifact.getArtifact();

        File localFile;
        try {
            localFile = download(ivyArtifact, moduleSource);
        } catch (IOException e) {
            result.failed(new ArtifactResolveException(artifact.getId(), e));
            return;
        }

        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact.getId());
        }
    }

    protected File download(Artifact artifact, ModuleSource moduleSource) throws IOException {
        return download(artifact);
    }

    protected File download(Artifact artifact) throws IOException {
        ResolvedArtifact artifactRef = getArtifactRef(artifact, true);
        if (artifactRef == null) {
            return null;
        }

        return repositoryCacheManager.downloadAndCacheArtifactFile(artifact.getId(), resourceDownloader, artifactRef.resource).getLocalResource().getFile();
    }

    private ExternalResource getResource(String source, ArtifactRevisionId target, boolean forDownload) {
        try {
            if (forDownload) {
                LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(target);
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

    private void get(ExternalResource resource, File destination) throws IOException {
        LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
        if (destination.getParentFile() != null) {
            GFileUtils.mkdirs(destination.getParentFile());
        }

        try {
            resource.writeTo(destination);
        } finally {
            resource.close();
        }
    }

    private void verifyChecksums(ExternalResource resource, File dest) throws IOException {
        String[] checksums = getChecksumAlgorithms();
        boolean checked = false;
        for (int i = 0; i < checksums.length && !checked; i++) {
            checked = check(resource, dest, checksums[i]);
        }
    }

    private boolean check(ExternalResource resource, File dest, String algorithm) throws IOException {
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
                LOGGER.debug("{} OK for {}", algorithm, resource);
                return true;
            } finally {
                csFile.delete();
            }
        } else {
            return false;
        }
    }

    // TODO:DAZ Remove the need for this, by using our own set of PatternMatchers
    public void setSettings(IvySettings settings) {
    }

    public void publish(ModuleVersionPublishMetaData moduleVersion) throws IOException {
        for (ModuleVersionArtifactPublishMetaData artifact : moduleVersion.getArtifacts()) {
            publish(artifact.getArtifact(), artifact.getFile());
        }
    }

    private void publish(Artifact artifact, File src) throws IOException {
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

    public boolean isM2compatible() {
        return m2Compatible;
    }

    public void setM2compatible(boolean compatible) {
        m2Compatible = compatible;
    }

    public boolean isCheckconsistency() {
        return checkConsistency;
    }

    public void setCheckconsistency(boolean checkConsistency) {
        this.checkConsistency = checkConsistency;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isAllownomd() {
        return allowMissingDescriptor;
    }

    public void setAllownomd(boolean allowMissingDescriptor) {
        this.allowMissingDescriptor = allowMissingDescriptor;
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

    public String getChangingMatcherName() {
        return changingMatcherName;
    }

    public void setChangingMatcher(String changingMatcherName) {
        this.changingMatcherName = changingMatcherName;
    }

    public String getChangingPattern() {
        return changingPattern;
    }

    public void setChangingPattern(String changingPattern) {
        this.changingPattern = changingPattern;
    }

    public void setRepositoryCacheManager(RepositoryArtifactCache repositoryCacheManager) {
        this.repositoryCacheManager = repositoryCacheManager;
    }

    protected ResourcePattern toResourcePattern(String pattern) {
        return isM2compatible() ? new M2ResourcePattern(pattern) : new IvyResourcePattern(pattern);
    }

    private boolean isChanging(ModuleDescriptor moduleDescriptor) {
        if (changingMatcherName == null || changingPattern == null) {
            return false;
        }
        PatternMatcher matcher = resolverStrategy.getPatternMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern).matches(moduleDescriptor.getResolvedModuleRevisionId().getRevision());
    }

    protected static class ResolvedArtifact {
        private final ExternalResource resource;
        private final Artifact artifact;

        public ResolvedArtifact(ExternalResource resource, Artifact artifact) {
            this.resource = resource;
            this.artifact = artifact;
        }

        protected ExternalResource getResource() {
            return resource;
        }

        protected Artifact getArtifact() {
            return artifact;
        }
    }

    protected static class DownloadedAndParsedMetaDataArtifact extends ResolvedArtifact {
        private final MutableModuleVersionMetaData moduleVersionMetaData;

        public DownloadedAndParsedMetaDataArtifact(ExternalResource resource, Artifact artifact, MutableModuleVersionMetaData moduleVersionMetaData) {
            super(resource, artifact);
            this.moduleVersionMetaData = moduleVersionMetaData;
        }
    }
}
