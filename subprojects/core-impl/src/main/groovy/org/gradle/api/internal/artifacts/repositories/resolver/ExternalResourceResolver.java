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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
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
import org.gradle.util.CollectionUtils;
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
    private final ResolverStrategy resolverStrategy;

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
                                    ResolverStrategy resolverStrategy) {
        this.name = name;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.metaDataParser = metaDataParser;
        this.metadataProcessor = metadataProcessor;
        this.resolverStrategy = resolverStrategy;
    }

    private ArtifactIdentifier toArtifactIdentifier(ArtifactRevisionId artifact) {
        return new DefaultArtifactIdentifier(artifact);
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
        ModuleIdentifier module  = new DefaultModuleIdentifier(dependency.getRequested().getGroup(), dependency.getRequested().getName());
        VersionList versionList = versionLister.getVersionList(module);
        // List modules based on metadata files
        ArtifactIdentifier metaDataArtifact = toArtifactIdentifier(getMetaDataArtifactFor(dependency));
        listVersionsForAllPatterns(module, getIvyPatterns(), metaDataArtifact, versionList);

        // List modules with missing metadata files
        // TODO:DAZ Should check isAllownomd()
        for (ArtifactRevisionId otherArtifact : getAllArtifacts(getDefaultMetaData(dependency))) {
            listVersionsForAllPatterns(module, getArtifactPatterns(), toArtifactIdentifier(otherArtifact), versionList);
        }
        DefaultModuleVersions moduleVersions = new DefaultModuleVersions();
        for (VersionList.ListedVersion listedVersion : versionList.getVersions()) {
            moduleVersions.add(listedVersion.getVersion());
        }
        result.listed(moduleVersions);
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        resolveStaticDependency(dependency, result, createArtifactResolver());
    }

    protected final void resolveStaticDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result, ArtifactResolver artifactResolver) {
        DownloadedAndParsedMetaDataArtifact metaDataArtifact = findMetaDataArtifact(dependency, artifactResolver);

        if (metaDataArtifact != null) {
            MutableModuleVersionMetaData moduleVersionMetaData = metaDataArtifact.moduleVersionMetaData;
            LOGGER.debug("Metadata file found for module '{}' in repository '{}'.", dependency.getRequested(), getName());
            result.resolved(moduleVersionMetaData, null);
            return;
        }

        if (isAllownomd()) {
            DownloadedAndParsedMetaDataArtifact defaultArtifact = findDefaultArtifact(dependency, artifactResolver);
            if (defaultArtifact != null) {
                LOGGER.debug("Artifact file found for module '{}' in repository '{}'.", dependency.getRequested(), getName());
                result.resolved(defaultArtifact.moduleVersionMetaData, null);
                return;
            }
        }

        LOGGER.debug("No meta-data file or artifact found for module '{}' in repository '{}'.", dependency.getRequested(), getName());
        result.missing();
    }

    protected DownloadedAndParsedMetaDataArtifact findMetaDataArtifact(DependencyMetaData dependency, ArtifactResolver artifactResolver) {
        ArtifactRevisionId artifactIdentifier = getMetaDataArtifactFor(dependency);
        if (artifactIdentifier == null) {
            return null;
        }
        ResolvedArtifact metaDataResource = artifactResolver.downloadMetaDataArtifact(artifactIdentifier);
        if (metaDataResource == null) {
            return null;
        }
        MutableModuleVersionMetaData moduleVersionMetaData = getArtifactMetadata(dependency, artifactIdentifier, metaDataResource.getResource());

        if (isCheckconsistency()) {
            ModuleVersionSelector requested = dependency.getRequested();
            ModuleVersionIdentifier requestedId = DefaultModuleVersionIdentifier.newId(requested.getGroup(), requested.getName(), requested.getVersion());
            checkMetadataConsistency(requestedId, moduleVersionMetaData, metaDataResource);
        }

        return new DownloadedAndParsedMetaDataArtifact(metaDataResource.resource, metaDataResource.artifactId, moduleVersionMetaData);
    }

    private DownloadedAndParsedMetaDataArtifact findDefaultArtifact(DependencyMetaData dependency, ArtifactResolver artifactResolver) {
        MutableModuleVersionMetaData metaData = getDefaultMetaData(dependency);

        ResolvedArtifact artifactRef = findAnyArtifact(metaData, artifactResolver);
        if (artifactRef == null) {
            return null;
        }

        LOGGER.debug("No meta-data file found for module '{}' in repository '{}', using default data instead.", dependency.getRequested(), getName());

        return new DownloadedAndParsedMetaDataArtifact(artifactRef.resource, artifactRef.artifactId, metaData);
    }

    protected MutableModuleVersionMetaData getDefaultMetaData(DependencyMetaData dependency) {
        ModuleRevisionId moduleRevisionId = dependency.getDescriptor().getDependencyRevisionId();
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(moduleRevisionId, dependency.getDescriptor().getAllDependencyArtifacts());
        moduleDescriptor.setStatus("integration");
        MutableModuleVersionMetaData rawMetaData = new ModuleDescriptorAdapter(moduleDescriptor);
        return processRawMetaData(rawMetaData);
    }

    protected MutableModuleVersionMetaData getArtifactMetadata(DependencyMetaData dependency, ArtifactRevisionId artifactId, ExternalResource resource) {
        ExternalResourceResolverDescriptorParseContext context = new ExternalResourceResolverDescriptorParseContext(nestedResolver, this, artifactId.getModuleRevisionId());
        LocallyAvailableExternalResource cachedResource;
        try {
            cachedResource = repositoryCacheManager.downloadAndCacheArtifactFile(artifactId, resourceDownloader, resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MutableModuleVersionMetaData rawMetaData =  metaDataParser.parseMetaData(context, cachedResource);
        return processRawMetaData(rawMetaData);
    }

    private MutableModuleVersionMetaData processRawMetaData(MutableModuleVersionMetaData rawMetaData) {
        rawMetaData.setChanging(isChanging(rawMetaData.getDescriptor()));
        MutableModuleVersionMetaData metaData = rawMetaData.copy();
        metaData.setRawMetaData(rawMetaData);
        metadataProcessor.process(metaData);
        return metaData;
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

    @Nullable
    protected abstract ArtifactRevisionId getMetaDataArtifactFor(DependencyMetaData dependency);

    protected ResolvedArtifact findAnyArtifact(ModuleVersionMetaData metaData, ArtifactResolver artifactResolver) {
        for (ArtifactRevisionId artifact : getAllArtifacts(metaData)) {
            ResolvedArtifact artifactRef = artifactResolver.resolveArtifact(artifact);
            if (artifactRef != null) {
                return artifactRef;
            }
        }
        return null;
    }

    private ArtifactRevisionId[] getAllArtifacts(ModuleVersionMetaData metaData) {
        return CollectionUtils.collectArray(metaData.getDescriptor().getAllArtifacts(), ArtifactRevisionId.class, new Transformer<ArtifactRevisionId, Artifact>() {
            public ArtifactRevisionId transform(Artifact original) {
                return original.getId();
            }
        });
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        ResolvedArtifact artifactRef = createArtifactResolver().resolveArtifact(artifact.getArtifact().getId());
        return artifactRef != null && artifactRef.resource.exists();
    }

    protected ArtifactResolver createArtifactResolver() {
        return new ArtifactResolver(getIvyPatterns(), getArtifactPatterns());
    }

    // TODO:DAZ Extract this properly: make this static
    protected class ArtifactResolver {
        private final List<String> ivyPatterns;
        private final List<String> artifactPatterns;

        public ArtifactResolver(List<String> ivyPatterns, List<String> artifactPatterns) {
            this.ivyPatterns = ivyPatterns;
            this.artifactPatterns = artifactPatterns;
        }

        public ResolvedArtifact downloadMetaDataArtifact(ArtifactRevisionId artifactRevisionId) {
            return findStaticResourceUsingPatterns(ivyPatterns, artifactRevisionId, true);
        }

        public ResolvedArtifact downloadArtifact(ArtifactRevisionId artifactRevisionId) {
            return findStaticResourceUsingPatterns(artifactPatterns, artifactRevisionId, true);
        }

        public ResolvedArtifact resolveArtifact(ArtifactRevisionId artifactRevisionId) {
            return findStaticResourceUsingPatterns(artifactPatterns, artifactRevisionId, false);
        }

        private ResolvedArtifact findStaticResourceUsingPatterns(List<String> patternList, ArtifactRevisionId artifactId, boolean forDownload) {
            for (String pattern : patternList) {
                ResourcePattern resourcePattern = toResourcePattern(pattern);
                String resourceName = resourcePattern.toPath(artifactId);
                LOGGER.debug("Loading {}", resourceName);
                ExternalResource resource = getResource(resourceName, artifactId, forDownload);
                if (resource.exists()) {
                    return new ResolvedArtifact(resource, artifactId);
                } else {
                    LOGGER.debug("Resource not reachable for {}: res={}", artifactId, resource);
                    discardResource(resource);
                }
            }
            return null;
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

        protected void discardResource(ExternalResource resource) {
            try {
                resource.close();
            } catch (IOException e) {
                LOGGER.warn("Exception closing resource " + resource.getName(), e);
            }
        }
    }

    private void listVersionsForAllPatterns(ModuleIdentifier module, List<String> patternList, ArtifactIdentifier artifactId, VersionList versionList) {
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            try {
                versionList.visit(resourcePattern, artifactId);
            } catch (ResourceNotFoundException e) {
                LOGGER.debug(String.format("Unable to load version list for %s from %s", module, getRepository()));
                // Don't add any versions
                // TODO:DAZ Should fail?
            }
        }
    }

    public void resolve(ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        ArtifactRevisionId ivyArtifact = artifact.getArtifact().getId();

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

    protected File download(ArtifactRevisionId artifactId, ModuleSource moduleSource) throws IOException {
        return downloadArtifact(artifactId, createArtifactResolver());
    }

    protected File downloadArtifact(ArtifactRevisionId artifactId, ArtifactResolver artifactResolver) throws IOException {
        ResolvedArtifact artifactRef = artifactResolver.downloadArtifact(artifactId);
        if (artifactRef == null) {
            return null;
        }

        return repositoryCacheManager.downloadAndCacheArtifactFile(artifactId, resourceDownloader, artifactRef.resource).getLocalResource().getFile();
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
            publish(artifact.getArtifact().getId(), artifact.getFile());
        }
    }

    private void publish(ArtifactRevisionId artifactId, File src) throws IOException {
        String destinationPattern;
        if ("ivy".equals(artifactId.getType()) && !getIvyPatterns().isEmpty()) {
            destinationPattern = getIvyPatterns().get(0);
        } else if (!getArtifactPatterns().isEmpty()) {
            destinationPattern = getArtifactPatterns().get(0);
        } else {
            throw new IllegalStateException("impossible to publish " + artifactId + " using " + this + ": no artifact pattern defined");
        }
        String destination = toResourcePattern(destinationPattern).toPath(artifactId);

        put(src, destination);
        LOGGER.info("Published {} to {}", artifactId.getName(), destination);
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
        private final ArtifactRevisionId artifactId;

        public ResolvedArtifact(ExternalResource resource, ArtifactRevisionId artifactId) {
            this.resource = resource;
            this.artifactId = artifactId;
        }

        protected ExternalResource getResource() {
            return resource;
        }

        protected ArtifactRevisionId getArtifactId() {
            return artifactId;
        }
    }

    protected static class DownloadedAndParsedMetaDataArtifact extends ResolvedArtifact {
        protected final MutableModuleVersionMetaData moduleVersionMetaData;

        public DownloadedAndParsedMetaDataArtifact(ExternalResource resource, ArtifactRevisionId artifactId, MutableModuleVersionMetaData moduleVersionMetaData) {
            super(resource, artifactId);
            this.moduleVersionMetaData = moduleVersionMetaData;
        }
    }
}
