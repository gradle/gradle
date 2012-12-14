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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyContextualiser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.repositories.cachemanager.EnhancedArtifactDownloadReport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.api.resources.ResourceException;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

public class MavenResolver extends ExternalResourceResolver implements PatternBasedResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);
    private final RepositoryTransport transport;
    private final String root;
    private final List<String> artifactRoots = new ArrayList<String>();
    private String pattern = MavenPattern.M2_PATTERN;
    private boolean usepoms = true;
    private boolean useMavenMetadata = true;
    private final MavenMetadataLoader mavenMetaDataLoader;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder) {
        super(name,
                transport.getRepository(),
                new ChainedVersionLister(new MavenVersionLister(transport.getRepository()), new ResourceVersionLister(transport.getRepository())),
                locallyAvailableResourceFinder);
        transport.configureCacheManager(this);

        this.mavenMetaDataLoader = new MavenMetadataLoader(transport.getRepository());
        this.transport = transport;
        this.root = transport.convertToPath(rootUri);

        setDescriptor(DESCRIPTOR_OPTIONAL);
        super.setM2compatible(true);

        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");

        updatePatterns();
    }

    public void getDependency(DependencyDescriptor dd, BuildableModuleVersionDescriptor result) {
        if (isSnapshotVersion(dd)) {
            getSnapshotDependency(dd, result);
        } else {
            super.getDependency(dd, result);
        }
    }

    private void getSnapshotDependency(DependencyDescriptor dd, BuildableModuleVersionDescriptor result) {
        final ModuleRevisionId dependencyRevisionId = dd.getDependencyRevisionId();
        final String uniqueSnapshotVersion = findUniqueSnapshotVersion(dependencyRevisionId);
        if (uniqueSnapshotVersion != null) {
            DependencyDescriptor enrichedDependencyDescriptor = enrichDependencyDescriptorWithSnapshotVersionInfo(dd, dependencyRevisionId, uniqueSnapshotVersion);
            super.getDependency(enrichedDependencyDescriptor, result);
            if (result.getState() == BuildableModuleVersionDescriptor.State.Resolved) {
                result.resolved(result.getDescriptor(), result.isChanging(), new TimestampedModuleSource(uniqueSnapshotVersion));
            }
        } else {
            super.getDependency(dd, result);
        }
    }

    private DependencyDescriptor enrichDependencyDescriptorWithSnapshotVersionInfo(DependencyDescriptor dd, ModuleRevisionId dependencyRevisionId, String uniqueSnapshotVersion) {
        Map<String, String> extraAttributes = new HashMap<String, String>(1);
        extraAttributes.put("timestamp", uniqueSnapshotVersion);
        final ModuleRevisionId newModuleRevisionId = ModuleRevisionId.newInstance(dependencyRevisionId.getOrganisation(), dependencyRevisionId.getName(), dependencyRevisionId.getRevision(), extraAttributes);
        return dd.clone(newModuleRevisionId);
    }

    private boolean isSnapshotVersion(DependencyDescriptor dd) {
        return dd.getDependencyRevisionId().getRevision().endsWith("SNAPSHOT");
    }

    protected EnhancedArtifactDownloadReport download(Artifact artifact, ModuleSource moduleSource) {
        EnhancedArtifactDownloadReport artifactDownloadReport;

        if (moduleSource instanceof TimestampedModuleSource) {
            TimestampedModuleSource timestampedModuleSource = (TimestampedModuleSource) moduleSource;
            String timestampedVersion = timestampedModuleSource.getTimestampedVersion();
            artifactDownloadReport = downloadTimestampedVersion(artifact, timestampedVersion);
        } else {
            artifactDownloadReport = download(artifact);
        }
        return artifactDownloadReport;
    }

    private EnhancedArtifactDownloadReport downloadTimestampedVersion(Artifact artifact, String timestampedVersion) {
        final ModuleRevisionId artifactModuleRevisionId = artifact.getModuleRevisionId();
        final ModuleRevisionId moduleRevisionId = ModuleRevisionId.newInstance(artifactModuleRevisionId.getOrganisation(),
                artifactModuleRevisionId.getName(),
                artifactModuleRevisionId.getRevision(),
                WrapUtil.toMap("timestamp", timestampedVersion));
        final Artifact artifactWithResolvedModuleRevisionId = DefaultArtifact.cloneWithAnotherMrid(artifact, moduleRevisionId);
        return download(artifactWithResolvedModuleRevisionId);
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        if (pattern != null && pattern.length() > 0) {
            throw new IllegalArgumentException("Maven Resolver only supports a single pattern. It cannot be provided on a per-location basis.");
        }
        artifactRoots.add(transport.convertToPath(baseUri));

        updatePatterns();
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        throw new UnsupportedOperationException("Cannot have multiple descriptor urls for MavenResolver");
    }

    private String getWholePattern() {
        return root + pattern;
    }

    private void updatePatterns() {
        if (isUsepoms()) {
            setIvyPatterns(Collections.singletonList(getWholePattern()));
        } else {
            setIvyPatterns(Collections.<String>emptyList());
        }

        List<String> artifactPatterns = new ArrayList<String>();
        artifactPatterns.add(getWholePattern());
        for (String artifactRoot : artifactRoots) {
            artifactPatterns.add(artifactRoot + pattern);
        }
        setArtifactPatterns(artifactPatterns);
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd) {
        if (isUsepoms()) {
            ModuleRevisionId moduleRevisionId = dd.getDependencyRevisionId();
            //we might need a own implementation of DefaultArtifact here as there is no way to pass extraAttributes AND isMetaData to DefaultArtifact
            Artifact pomArtifact = new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.getName(), "pom", "pom", moduleRevisionId.getExtraAttributes());
            ResolveData data = IvyContextualiser.getIvyContext().getResolveData();
            ResourceMDParser parser = getRMDParser(dd, data);
            return findResourceUsingPatterns(moduleRevisionId, getIvyPatterns(), pomArtifact, parser, null, true);
        }

        return null;
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        ResourceMDParser parser = getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId());
        return findResourceUsingPatterns(moduleRevisionId, getArtifactPatterns(), artifact, parser, date, forDownload);
    }

    private String findUniqueSnapshotVersion(ModuleRevisionId moduleRevisionId) {
        Artifact pomArtifact = DefaultArtifact.newPomArtifact(moduleRevisionId, new Date());
        String metadataLocation = toResourcePattern(getWholePattern()).toModuleVersionPath(pomArtifact) + "/maven-metadata.xml";
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String rev = moduleRevisionId.getRevision();
            rev = rev.substring(0, rev.length() - "SNAPSHOT".length());
            rev = rev + mavenMetadata.timestamp + "-" + mavenMetadata.buildNumber;
            return rev;
        }
        return null;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation) {
        if (shouldUseMavenMetadata(pattern)) {
            try {
                return mavenMetaDataLoader.load(metadataLocation);
            } catch (ResourceNotFoundException e) {
                return new MavenMetadata();
            } catch (ResourceException e) {
                LOGGER.warn("impossible to access maven metadata file, ignored.", e);
            }
        }
        return new MavenMetadata();
    }

    // A bunch of configuration properties that we don't (yet) support in our model via the DSL. Users can still tweak these on the resolver using mavenRepo().
    public boolean isUsepoms() {
        return usepoms;
    }

    public void setUsepoms(boolean usepoms) {
        this.usepoms = usepoms;
        updatePatterns();
    }

    public boolean isUseMavenMetadata() {
        return useMavenMetadata;
    }

    @Deprecated
    public void setUseMavenMetadata(boolean useMavenMetadata) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("MavenResolver.setUseMavenMetadata(boolean)");
        this.useMavenMetadata = useMavenMetadata;
        if (useMavenMetadata) {
            this.versionLister = new ChainedVersionLister(
                    new MavenVersionLister(getRepository()),
                    new ResourceVersionLister(getRepository()));
        } else {
            this.versionLister = new ResourceVersionLister(getRepository());
        }
    }

    private boolean shouldUseMavenMetadata(String pattern) {
        return isUseMavenMetadata() && pattern.endsWith(MavenPattern.M2_PATTERN);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern must not be null");
        }
        this.pattern = pattern;
        updatePatterns();
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        throw new UnsupportedOperationException("Cannot configure root on mavenRepo. Use 'url' property instead.");
    }

    @Override
    public void setM2compatible(boolean compatible) {
        if (!compatible) {
            throw new IllegalArgumentException("Cannot set m2compatible = false on mavenRepo.");
        }
    }

    protected static class TimestampedModuleSource implements ModuleSource {
        public String getTimestampedVersion() {
            return timestampedVersion;
        }

        private final String timestampedVersion;

        public TimestampedModuleSource(String uniqueSnapshotVersion) {
            this.timestampedVersion = uniqueSnapshotVersion;
        }
    }
}
