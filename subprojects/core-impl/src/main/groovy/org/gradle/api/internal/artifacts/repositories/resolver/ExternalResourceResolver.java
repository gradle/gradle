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
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.latest.LatestStrategy;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyResolverIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.ExternalResourceResolverDependencyResolver;
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
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache.ExternalResourceDownloader;

// TODO:DAZ Implement ModuleVersionRepository directly, or add an API
public class ExternalResourceResolver implements ModuleVersionPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private final MetaDataParser metaDataParser;

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
    private RepositoryArtifactCache repositoryCacheManager;
    private String changingMatcherName;
    private String changingPattern;

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;

    protected VersionLister versionLister;

    // TODO:DAZ Get rid of this
    private final ExternalResourceDownloader resourceDownloader = new ExternalResourceDownloader() {
        public void download(Artifact artifact, ExternalResource resource, File dest) throws IOException {
            get(resource, dest);
            verifyChecksums(resource, dest);
        }
    };

    public ExternalResourceResolver(String name,
                                    ExternalResourceRepository repository,
                                    VersionLister versionLister,
                                    LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                    MetaDataParser metaDataParser
    ) {
        this.name = name;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.metaDataParser = metaDataParser;
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

    public String toString() {
        return String.format("Repository '%s'", getName());
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

    public boolean isLocal() {
        return repositoryCacheManager.isLocal();
    }

    public void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaDataResolveResult result) {
        ModuleRevisionId moduleRevisionId = dependencyDescriptor.getDependencyRevisionId();

        boolean isDynamic = getVersionMatcher().isDynamic(moduleRevisionId);

        ResolvedArtifact ivyRef = findIvyFileRef(dependencyDescriptor);

        // get module descriptor
        if (ivyRef == null) {
            if (!isAllownomd()) {
                LOGGER.debug("No ivy file found for module '{}' in repository '{}'.", moduleRevisionId, getName());
                result.missing();
                return;
            }
            DefaultModuleDescriptor nsMd = DefaultModuleDescriptor.newDefaultInstance(moduleRevisionId, dependencyDescriptor.getAllDependencyArtifacts());
            ResolvedArtifact artifactRef = findFirstArtifactRef(nsMd);
            if (artifactRef == null) {
                LOGGER.debug("No ivy file nor artifact found for module '{}' in repository '{}'.", moduleRevisionId, getName());
                result.missing();
            } else {
                long lastModified = artifactRef.resource.getLastModified();
                if (lastModified != 0) {
                    nsMd.setLastModified(lastModified);
                }
                LOGGER.debug("No ivy file found for module '{}' in repository '{}', using default data instead.", moduleRevisionId, getName());
                if (isDynamic) {
                    nsMd.setResolvedModuleRevisionId(artifactRef.artifact.getModuleRevisionId());
                }
                result.resolved(nsMd, isChanging(nsMd), null);
            }
        } else {
            try {
                ModuleDescriptor nsMd;
                if (ivyRef instanceof MDResolvedResource) {
                    nsMd = ((MDResolvedResource) ivyRef).moduleDescriptor;
                } else {
                    nsMd = parse(ivyRef.artifact, ivyRef.resource);
                }

                // check descriptor data is in sync with resource revision and names
                if (isCheckconsistency()) {
                    checkDescriptorConsistency(moduleRevisionId, nsMd, ivyRef);
                }
                LOGGER.debug("Ivy file found for module '{}' in repository '{}'.", moduleRevisionId, getName());
                result.resolved(nsMd, isChanging(nsMd), null);
            } catch (MetaDataParseException e) {
                result.failed(new ModuleVersionResolveException(moduleRevisionId, e));
            }
        }
    }

    private VersionMatcher getVersionMatcher() {
        return getSettings().getVersionMatcher();
    }

    private ModuleDescriptor parse(Artifact artifact, ExternalResource resource) {
        ModuleRevisionId dependencyRevisionId = artifact.getModuleRevisionId();

        LocallyAvailableExternalResource cachedResource;
        try {
            cachedResource = repositoryCacheManager.downloadAndCacheArtifactFile(artifact, resourceDownloader, resource);
        } catch (IOException e) {
            // TODO:DAZ Work out if/when/why this happens
            LOGGER.warn("Problem while downloading module descriptor: {}: {}", resource, e.getMessage());
            return null;
        }

        return metaDataParser.parseModuleDescriptor(dependencyRevisionId, cachedResource, new ExternalResourceResolverDependencyResolver(this));
    }

    private void checkDescriptorConsistency(ModuleRevisionId mrid, ModuleDescriptor md,
                                            ResolvedArtifact ivyRef) throws MetaDataParseException {
        List<String> errors = new ArrayList<String>();
        if (!mrid.getOrganisation().equals(md.getModuleRevisionId().getOrganisation())) {
            errors.add("bad organisation: expected='" + mrid.getOrganisation() + "' found='" + md.getModuleRevisionId().getOrganisation() + "'");
        }
        if (!mrid.getName().equals(md.getModuleRevisionId().getName())) {
            errors.add("bad module name: expected='" + mrid.getName() + "' found='" + md.getModuleRevisionId().getName() + "'");
        }
        if (mrid.getBranch() != null && !mrid.getBranch().equals(md.getModuleRevisionId().getBranch())) {
            errors.add("bad branch name: expected='" + mrid.getBranch() + "' found='" + md.getModuleRevisionId().getBranch() + "'");
        }
        String revision = ivyRef.artifact.getModuleRevisionId().getRevision();
        if (revision != null && !revision.startsWith("working@")) {
            ModuleRevisionId expectedMrid = ModuleRevisionId.newInstance(mrid, revision);
            if (!getVersionMatcher().accept(expectedMrid, md)) {
                errors.add("bad revision: expected='" + revision + "' found='" + md.getModuleRevisionId().getRevision() + "'");
            }
        }
        if (!getSettings().getStatusManager().isStatus(md.getStatus())) {
            errors.add("bad status: '" + md.getStatus() + "'; ");
        }
        if (errors.size() > 0) {
            throw new MetaDataParseException(String.format("inconsistent module descriptor file found in '%s': %s", ivyRef.resource, errors.toString()));
        }
    }

    protected ResolvedArtifact findIvyFileRef(DependencyDescriptor dd) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        Artifact artifact = DefaultArtifact.newIvyArtifact(mrid, null);
        return findResourceUsingPatterns(mrid, ivyPatterns, artifact, getRMDParser(), null, true);
    }

    private ResolvedArtifact findFirstArtifactRef(ModuleDescriptor md) {
        for (String configuration : md.getConfigurationsNames()) {
            for (Artifact artifact : md.getArtifacts(configuration)) {
                ResolvedArtifact artifactRef = getArtifactRef(artifact, null, false);
                if (artifactRef != null) {
                    return artifactRef;
                }
            }
        }
        return null;
    }

    public ArtifactOrigin locate(Artifact artifact) {
        ResolvedArtifact artifactRef = getArtifactRef(artifact, null, false);
        if (artifactRef != null && artifactRef.resource.exists()) {
            ExternalResource resource = artifactRef.resource;
            return new ArtifactOrigin(artifact, resource.isLocal(), resource.getName());
        }
        return null;
    }

    private ResolvedArtifact getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        ResourceMDParser parser = getDefaultRMDParser();
        return findResourceUsingPatterns(moduleRevisionId, getArtifactPatterns(), artifact, parser, date, forDownload);
    }

    protected ResourceMDParser getRMDParser() {
        return new ResourceMDParser() {
            public MDResolvedResource parse(ExternalResource resource, Artifact artifact) {
                ModuleDescriptor md = ExternalResourceResolver.this.parse(artifact, resource);
                if (md == null) {
                    return null;
                } else {
                    return new MDResolvedResource(resource, artifact, md);
                }
            }
        };
    }

    protected ResourceMDParser getDefaultRMDParser() {
        return new ResourceMDParser() {
            public MDResolvedResource parse(ExternalResource resource, Artifact artifact) {
                DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(artifact.getModuleRevisionId());
                md.setStatus("integration");
                return new MDResolvedResource(resource, artifact, md);
            }
        };
    }

    protected ResolvedArtifact findResourceUsingPatterns(ModuleRevisionId requestedModuleRevision, List<String> patternList, Artifact artifact, ResourceMDParser rmdparser, Date date, boolean forDownload) {
        if (getVersionMatcher().isDynamic(requestedModuleRevision)) {
            return findDynamicResourceUsingPatterns(requestedModuleRevision, patternList, artifact, rmdparser, date, forDownload);
        } else {
            return findStaticResourceUsingPatterns(requestedModuleRevision, patternList, artifact, forDownload);
        }
    }

    private ResolvedArtifact findStaticResourceUsingPatterns(ModuleRevisionId moduleRevision, List<String> patternList, Artifact artifact, boolean forDownload) {
        // Static version, return first found
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            String resourceName = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourceName);
            ExternalResource resource = getResource(resourceName, artifact, forDownload);
            if (resource.exists()) {
                return new ResolvedArtifact(resource, artifact);
            } else {
                LOGGER.debug("Resource not reachable for {}: res={}", moduleRevision, resource);
            }
        }
        return null;
    }

    private ResolvedArtifact findDynamicResourceUsingPatterns(ModuleRevisionId requestedModuleRevision, List<String> patternList, Artifact artifact, ResourceMDParser rmdparser, Date date, boolean forDownload) {
        // Dynamic version: list all, then choose latest
        VersionList versionList = listVersionsForAllPatterns(requestedModuleRevision, patternList, artifact);
        return findLatestResource(requestedModuleRevision, versionList, rmdparser, date, artifact, forDownload);
    }

    private VersionList listVersionsForAllPatterns(ModuleRevisionId requestedModuleRevision, List<String> patternList, Artifact artifact) {
        VersionList versionList = versionLister.getVersionList(requestedModuleRevision);
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            try {
                versionList.visit(resourcePattern, artifact);
            } catch (ResourceNotFoundException e) {
                LOGGER.debug(String.format("Unable to load version list for %s from %s", requestedModuleRevision.getModuleId(), getRepository()));
                // Don't add any versions
                // TODO:DAZ Should fail?
            }
        }
        return versionList;
    }

    private ResolvedArtifact findLatestResource(ModuleRevisionId mrid, VersionList versions, ResourceMDParser rmdparser, Date date, Artifact artifact, boolean forDownload) {
        String name = getName();
        VersionMatcher versionMatcher = getVersionMatcher();
        for (VersionList.ListedVersion listedVersion : versions.sortLatestFirst(getLatestStrategy())) {
            String version = listedVersion.getVersion();

            ModuleRevisionId foundMrid = ModuleRevisionId.newInstance(mrid, version);

            if (!versionMatcher.accept(mrid, foundMrid)) {
                LOGGER.debug(name + ": rejected by version matcher: " + version);
                continue;
            }

            boolean needsModuleDescriptor = versionMatcher.needModuleDescriptor(mrid, foundMrid);
            artifact = DefaultArtifact.cloneWithAnotherMrid(artifact, foundMrid);
            String resourcePath = listedVersion.getPattern().toPath(artifact);
            ExternalResource resource = getResource(resourcePath, artifact, forDownload || needsModuleDescriptor);
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
                MDResolvedResource parsedResource = rmdparser.parse(resource, artifact);
                if (parsedResource == null) {
                    LOGGER.debug(name + ": impossible to get module descriptor resource: " + description);
                    discardResource(resource);
                    continue;
                }
                ModuleDescriptor md = parsedResource.moduleDescriptor;
                if (!versionMatcher.accept(mrid, md)) {
                    LOGGER.debug(name + ": md rejected by version matcher: " + description);
                    discardResource(resource);
                    continue;
                }

                return parsedResource;
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

    public void resolve(ArtifactIdentifier artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        Artifact ivyArtifact = DefaultArtifactIdentifier.toArtifact(artifact);

        File localFile;
        try {
            localFile = download(ivyArtifact, moduleSource);
        } catch (IOException e) {
            result.failed(new ArtifactResolveException(artifact, e));
            return;
        }

        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact);
        }
    }

    protected File download(Artifact artifact, ModuleSource moduleSource) throws IOException {
        return download(artifact);
    }

    protected File download(Artifact artifact) throws IOException {
        ResolvedArtifact artifactRef = getArtifactRef(artifact, null, true);
        if (artifactRef == null) {
            return null;
        }

        return repositoryCacheManager.downloadAndCacheArtifactFile(artifact, resourceDownloader, artifactRef.resource).getLocalResource().getFile();
    }

    private ExternalResource getResource(String source, Artifact target, boolean forDownload) {
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

    public void setSettings(IvySettings settings) {
        this.settings = settings;
    }

    public void publish(ModuleVersionPublishMetaData moduleVersion) throws IOException {
        for (Map.Entry<Artifact, File> entry : moduleVersion.getArtifacts().entrySet()) {
            publish(entry.getKey(), entry.getValue());
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
        allownomd = b;
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
                LOGGER.debug("{}: no latest strategy defined: using default", getName());
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
        PatternMatcher matcher = settings.getMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern).matches(moduleDescriptor.getResolvedModuleRevisionId().getRevision());
    }

    public interface ResourceMDParser {
        MDResolvedResource parse(ExternalResource resource, Artifact artifact);
    }

    public static class ResolvedArtifact {
        private final ExternalResource resource;
        private final Artifact artifact;

        public ResolvedArtifact(ExternalResource resource, Artifact artifact) {
            this.resource = resource;
            this.artifact = artifact;
        }
    }

    public static class MDResolvedResource extends ResolvedArtifact {
        private final ModuleDescriptor moduleDescriptor;

        public MDResolvedResource(ExternalResource resource, Artifact artifact, ModuleDescriptor moduleDescriptor) {
            super(resource, artifact);
            this.moduleDescriptor = moduleDescriptor;
        }
    }

}
