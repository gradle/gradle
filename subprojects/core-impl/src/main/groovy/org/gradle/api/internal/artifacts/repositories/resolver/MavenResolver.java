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
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.api.resources.ResourceException;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
            setIvyPatterns(Collections.EMPTY_LIST);
        }

        List<String> artifactPatterns = new ArrayList<String>();
        artifactPatterns.add(getWholePattern());
        for (String artifactRoot : artifactRoots) {
            artifactPatterns.add(artifactRoot + pattern);
        }
        setArtifactPatterns(artifactPatterns);
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        if (isUsepoms()) {
            ModuleRevisionId moduleRevisionId = dd.getDependencyRevisionId();

            if (moduleRevisionId.getRevision().endsWith("SNAPSHOT")) {
                ResolvedResource resolvedResource = findSnapshotDescriptor(dd, data, moduleRevisionId, true);
                if (resolvedResource != null) {
                    return resolvedResource;
                }
            }

            Artifact pomArtifact = DefaultArtifact.newPomArtifact(moduleRevisionId, data.getDate());
            ResourceMDParser parser = getRMDParser(dd, data);
            return findResourceUsingPatterns(moduleRevisionId, getIvyPatterns(), pomArtifact, parser, data.getDate(), true);
        }

        return null;
    }

    private ResolvedResource findSnapshotDescriptor(DependencyDescriptor dd, ResolveData data, ModuleRevisionId moduleRevisionId, boolean forDownload) {
        String rev = findUniqueSnapshotVersion(moduleRevisionId);
        if (rev != null) {
            // here it would be nice to be able to store the resolved snapshot version, to avoid
            // having to follow the same process to download artifacts
            LOGGER.debug("[{}] {}", rev, moduleRevisionId);

            // replace the revision token in file name with the resolved revision
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            Artifact pomArtifact = DefaultArtifact.newPomArtifact(moduleRevisionId, data.getDate());
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            return findResourceUsingPattern(moduleRevisionId, resourcePattern, pomArtifact, getRMDParser(dd, data), data.getDate(), forDownload);
        }
        return null;
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();

        if (moduleRevisionId.getRevision().endsWith("SNAPSHOT")) {
            ResolvedResource resolvedResource = findSnapshotArtifact(artifact, date, moduleRevisionId, forDownload);
            if (resolvedResource != null) {
                return resolvedResource;
            }
        }
        ResourceMDParser parser = getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId());
        return findResourceUsingPatterns(moduleRevisionId, getArtifactPatterns(), artifact, parser, date, forDownload);
    }

    private ResolvedResource findSnapshotArtifact(Artifact artifact, Date date, ModuleRevisionId moduleRevisionId, boolean forDownload) {
        String rev = findUniqueSnapshotVersion(moduleRevisionId);
        if (rev != null) {
            // replace the revision token in file name with the resolved revision
            // TODO:DAZ We're not using all available artifact patterns here, only the "main" pattern. This means that snapshot artifacts will not be resolved in additional artifact urls.
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            ResourcePattern resourcePattern = toResourcePattern(pattern);
            return findResourceUsingPattern(moduleRevisionId, resourcePattern, artifact, getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date, forDownload);
        }
        return null;
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

    public void dumpSettings() {
        super.dumpSettings();
        Message.debug("\t\troot: " + root);
        Message.debug("\t\tpattern: " + pattern);
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
}
