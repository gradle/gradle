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
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
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

public class ExternalResourceResolver implements ModuleVersionPublisher, ConfiguredModuleVersionRepository {
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

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        getDependency(dependency.getDescriptor(), result);
    }

    protected void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaDataResolveResult result) {
        ModuleRevisionId moduleRevisionId = dependencyDescriptor.getDependencyRevisionId();
        boolean isDynamic = versionMatcher.isDynamic(moduleRevisionId.getRevision());

        ResolvedArtifact ivyRef = findIvyFileRef(dependencyDescriptor);

        // get module descriptor
        if (ivyRef == null) {
            getDependencyForMissingIvyFileRef(dependencyDescriptor, result, moduleRevisionId, isDynamic);
        } else {
            getDependencyForFoundIvyFileRef(dependencyDescriptor, result, moduleRevisionId, ivyRef);
        }
    }

    private void getDependencyForMissingIvyFileRef(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaDataResolveResult result, ModuleRevisionId moduleRevisionId, boolean isDynamic) {
        if (!isAllownomd()) {
            LOGGER.debug("No ivy file found for module '{}' in repository '{}'.", moduleRevisionId, getName());
            result.missing();
            return;
        }

        DefaultModuleDescriptor generatedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(moduleRevisionId, dependencyDescriptor.getAllDependencyArtifacts());

        ResolvedArtifact artifactRef = findAnyArtifact(generatedModuleDescriptor);
        if (artifactRef == null) {
            LOGGER.debug("No ivy file nor artifact found for module '{}' in repository '{}'.", moduleRevisionId, getName());
            result.missing();
        } else {
            long lastModified = artifactRef.resource.getLastModified();
            if (lastModified != 0) {
                generatedModuleDescriptor.setLastModified(lastModified);
            }
            LOGGER.debug("No ivy file found for module '{}' in repository '{}', using default data instead.", moduleRevisionId, getName());
            if (isDynamic) {
                generatedModuleDescriptor.setResolvedModuleRevisionId(artifactRef.artifact.getModuleRevisionId());
            }

            ModuleDescriptorAdapter metaData = new ModuleDescriptorAdapter(generatedModuleDescriptor);
            metaData.setChanging(isChanging(generatedModuleDescriptor));

            result.resolved(metaData, null);
        }
    }

    void getDependencyForFoundIvyFileRef(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaDataResolveResult result, ModuleRevisionId moduleRevisionId, ResolvedArtifact ivyRef) {
        try {
            resolveArtifact(ivyRef, moduleRevisionId, result);
        } catch (MetaDataParseException e) {
            result.failed(new ModuleVersionResolveException(moduleRevisionId, e));
        }
    }

    private void resolveArtifact(ResolvedArtifact ivyRef, ModuleRevisionId moduleRevisionId, BuildableModuleVersionMetaDataResolveResult result) throws MetaDataParseException {
        MutableModuleVersionMetaData moduleVersionMetaData;
        if (ivyRef instanceof DownloadedAndParsedMetaDataArtifact) {
            moduleVersionMetaData = ((DownloadedAndParsedMetaDataArtifact) ivyRef).getModuleVersionMetaData();
        } else {
            moduleVersionMetaData = getArtifactMetadata(ivyRef.getArtifact(), ivyRef.getResource());
        }

        if (isCheckconsistency()) {
            checkMetadataConsistency(DefaultModuleVersionSelector.newSelector(moduleRevisionId), moduleVersionMetaData, ivyRef);
        }
        LOGGER.debug("Ivy file found for module '{}' in repository '{}'.", moduleRevisionId, getName());
        moduleVersionMetaData.setChanging(isChanging(moduleVersionMetaData.getDescriptor()));
        result.resolved(moduleVersionMetaData, null);
    }

    protected MutableModuleVersionMetaData getArtifactMetadata(Artifact artifact, ExternalResource resource) {
        MutableModuleVersionMetaData metadata = doGetArtifactMetadata(artifact, resource);
        metadataProcessor.process(metadata);
        return metadata;
    }

    private MutableModuleVersionMetaData doGetArtifactMetadata(Artifact artifact, ExternalResource resource) {
        if (artifact.isMetadata()) {
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
        } else {
            // Create dummy metadata where no metadata artifact exists
            DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(artifact.getModuleRevisionId());
            md.setStatus("integration");
            return new ModuleDescriptorAdapter(md);
        }
    }

    protected void checkMetadataConsistency(ModuleVersionSelector selector, ModuleVersionMetaData metadata,
                                          ResolvedArtifact ivyRef) throws MetaDataParseException {
        List<String> errors = new ArrayList<String>();
        if (!selector.getGroup().equals(metadata.getId().getGroup())) {
            errors.add("bad group: expected='" + selector.getGroup() + "' found='" + metadata.getId().getGroup() + "'");
        }
        if (!selector.getName().equals(metadata.getId().getName())) {
            errors.add("bad module name: expected='" + selector.getName() + "' found='" + metadata.getId().getName() + "'");
        }
        String revision = ivyRef.artifact.getModuleRevisionId().getRevision();
        if (revision != null && !revision.startsWith("working@")) {
            if (!versionMatcher.accept(revision, metadata)) {
                errors.add("bad version: expected='" + revision + "' found='" + metadata.getId().getVersion() + "'");
            }
        }
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            errors.add("bad status: '" + metadata.getStatus() + "'; ");
        }
        if (errors.size() > 0) {
            throw new MetaDataParseException(String.format("inconsistent module metadata found. Descriptor: %s Errors: %s",
                    ivyRef.resource, Joiner.on(SystemProperties.getLineSeparator()).join(errors)));
        }
    }

    protected ResolvedArtifact findIvyFileRef(DependencyDescriptor dd) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        Artifact artifact = DefaultArtifact.newIvyArtifact(mrid, null);
        return findResourceUsingPatterns(DefaultModuleVersionSelector.newSelector(mrid), ivyPatterns, artifact, true);
    }

    protected ResolvedArtifact findAnyArtifact(ModuleDescriptor md) {
        for (Artifact artifact : md.getAllArtifacts()) {
            ResolvedArtifact artifactRef = getArtifactRef(artifact, false);
            if (artifactRef != null) {
                return artifactRef;
            }
        }
        return null;
    }

    public boolean artifactExists(Artifact artifact) {
        ResolvedArtifact artifactRef = getArtifactRef(artifact, false);
        return artifactRef != null && artifactRef.resource.exists();
    }

    private ResolvedArtifact getArtifactRef(Artifact artifact, boolean forDownload) {
        ModuleVersionSelector selector = DefaultModuleVersionSelector.newSelector(artifact.getModuleRevisionId());
        return findResourceUsingPatterns(selector, getArtifactPatterns(), artifact, forDownload);
    }

    protected ResolvedArtifact findResourceUsingPatterns(ModuleVersionSelector selector, List<String> patternList, Artifact artifact, boolean forDownload) {
        if (versionMatcher.isDynamic(selector.getVersion())) {
            return findDynamicResourceUsingPatterns(selector, patternList, artifact, forDownload);
        } else {
            return findStaticResourceUsingPatterns(selector, patternList, artifact, forDownload);
        }
    }

    private ResolvedArtifact findStaticResourceUsingPatterns(ModuleVersionSelector selector, List<String> patternList, Artifact artifact, boolean forDownload) {
        // Static version, return first found
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            String resourceName = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourceName);
            ExternalResource resource = getResource(resourceName, artifact.getId(), forDownload);
            if (resource.exists()) {
                return new ResolvedArtifact(resource, artifact);
            } else {
                LOGGER.debug("Resource not reachable for {}: res={}", selector, resource);
            }
        }
        return null;
    }

    private ResolvedArtifact findDynamicResourceUsingPatterns(ModuleVersionSelector selector, List<String> patternList, Artifact artifact, boolean forDownload) {
        // Dynamic version: list all, then choose latest
        VersionList versionList = listVersionsForAllPatterns(selector, patternList, artifact);
        return findLatestResource(selector, versionList, artifact, forDownload);
    }

    private VersionList listVersionsForAllPatterns(ModuleVersionSelector selector, List<String> patternList, Artifact artifact) {
        VersionList versionList = versionLister.getVersionList(selector);
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            try {
                versionList.visit(resourcePattern, artifact);
            } catch (ResourceNotFoundException e) {
                LOGGER.debug(String.format("Unable to load version list for %s from %s", selector, getRepository()));
                // Don't add any versions
                // TODO:DAZ Should fail?
            }
        }
        return versionList;
    }

    private ResolvedArtifact findLatestResource(ModuleVersionSelector selector, VersionList versions, Artifact artifact, boolean forDownload) {
        String requestedVersion = selector.getVersion();
        String name = getName();

        for (VersionList.ListedVersion listedVersion : versions.sortLatestFirst(latestStrategy)) {
            String foundVersion = listedVersion.getVersion();

            boolean needsMetadata = versionMatcher.needModuleMetadata(requestedVersion, foundVersion);
            if (!needsMetadata && !versionMatcher.accept(requestedVersion, foundVersion)) {
                LOGGER.debug(name + ": rejected by version matcher: " + foundVersion);
                continue;
            }

            ModuleRevisionId revision = IvyUtil.createModuleRevisionId(selector.getGroup(), selector.getName(), foundVersion, null);
            artifact = DefaultArtifact.cloneWithAnotherMrid(artifact, revision);
            String resourcePath = listedVersion.getPattern().toPath(artifact);
            ExternalResource resource = getResource(resourcePath, artifact.getId(), forDownload || needsMetadata);
            String description = foundVersion + " [" + resource + "]";
            if (!resource.exists()) {
                LOGGER.debug(name + ": unreachable: " + description);
                discardResource(resource);
                continue;
            }
            if (needsMetadata) {
                MutableModuleVersionMetaData metaData = getArtifactMetadata(artifact, resource);
                if (metaData == null) {
                    LOGGER.debug(name + ": impossible to get module descriptor resource: " + description);
                    discardResource(resource);
                    continue;
                }
                if (!versionMatcher.accept(requestedVersion, metaData)) {
                    LOGGER.debug(name + ": md rejected by version matcher: " + description);
                    discardResource(resource);
                    continue;
                }

                return new DownloadedAndParsedMetaDataArtifact(resource, artifact, metaData);
            }
            return new ResolvedArtifact(resource, artifact);
        }
        return null;
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
            return false; // TODO: tell from module metadata (rule)
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

    private static class DownloadedAndParsedMetaDataArtifact extends ResolvedArtifact {
        private final MutableModuleVersionMetaData moduleVersionMetaData;

        public DownloadedAndParsedMetaDataArtifact(ExternalResource resource, Artifact artifact, MutableModuleVersionMetaData moduleVersionMetaData) {
            super(resource, artifact);
            this.moduleVersionMetaData = moduleVersionMetaData;
        }

        protected MutableModuleVersionMetaData getModuleVersionMetaData() {
            return moduleVersionMetaData;
        }
    }
}
