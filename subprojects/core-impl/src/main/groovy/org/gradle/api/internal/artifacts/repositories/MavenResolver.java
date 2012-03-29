/*
 * Copyright 2011 the original author or authors.
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
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.util.ContextualSAXHandler;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.XMLHelper;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

public class MavenResolver extends ExternalResourceResolver implements PatternBasedResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    private static final String M2_PER_MODULE_PATTERN = "[revision]/[artifact]-[revision](-[classifier]).[ext]";
    private static final String M2_PATTERN = "[organisation]/[module]/" + M2_PER_MODULE_PATTERN;

    private final RepositoryTransport transport;
    private final String root;
    private final List<String> artifactRoots = new ArrayList<String>();
    private String pattern = M2_PATTERN;
    private boolean usepoms = true;
    private boolean useMavenMetadata = true;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                         CachedExternalResourceIndex<String> cachedExternalResourceIndex) {
        super(name, transport.getRepository(), locallyAvailableResourceFinder, cachedExternalResourceIndex);
        transport.configureCacheManager(this);

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
        if (shouldResolveDependencyDescriptors()) {
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
        if (shouldResolveDependencyDescriptors()) {
            ModuleRevisionId moduleRevisionId = convertM2IdForResourceSearch(dd.getDependencyRevisionId());

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
            return findResourceUsingPattern(moduleRevisionId, pattern, pomArtifact, getRMDParser(dd, data), data.getDate(), forDownload);
        }
        return null;
    }

    protected ResolvedResource getArtifactRef(Artifact artifact, Date date, boolean forDownload) {
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        if (isM2compatible()) {
            moduleRevisionId = convertM2IdForResourceSearch(moduleRevisionId);
        }

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
            return findResourceUsingPattern(moduleRevisionId, pattern, artifact, getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date, forDownload);
        }
        return null;
    }

    private String findUniqueSnapshotVersion(ModuleRevisionId moduleRevisionId) {
        String metadataLocation = IvyPatternHelper.substitute(root + "[organisation]/[module]/[revision]/maven-metadata.xml", moduleRevisionId);
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

    @Override
    protected String[] listVersions(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) {
        List<String> revisions = listRevisionsWithMavenMetadata(moduleRevisionId.getModuleId().getAttributes());
        if (revisions != null) {
            return revisions.toArray(new String[revisions.size()]);
        }
        return super.listVersions(moduleRevisionId, pattern, artifact);
    }

    private List<String> listRevisionsWithMavenMetadata(Map tokenValues) {
        String metadataLocation = IvyPatternHelper.substituteTokens(root + "[organisation]/[module]/maven-metadata.xml", tokenValues);
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);
        return mavenMetadata.versions.isEmpty() ? null : mavenMetadata.versions;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation) {
        final MavenMetadata mavenMetadata = new MavenMetadata();

        if (shouldUseMavenMetadata(pattern)) {
            parseMavenMetadataInto(metadataLocation, mavenMetadata);
        }

        return mavenMetadata;
    }

    private void parseMavenMetadataInto(String metadataLocation, final MavenMetadata mavenMetadata) {
        try {
            Resource metadata = getResource(metadataLocation);
            try {
                parseMavenMetadataInto(metadata, mavenMetadata);
            } finally {
                discardResource(metadata);
            }
        } catch (IOException e) {
            LOGGER.warn("impossible to access maven metadata file, ignored.", e);
        } catch (SAXException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        } catch (ParserConfigurationException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        }
    }

    private void parseMavenMetadataInto(Resource metadataResource, final MavenMetadata mavenMetadata) throws IOException, SAXException, ParserConfigurationException {
        if (metadataResource.exists()) {
            LOGGER.debug("parsing maven-metadata: {}", metadataResource);
            InputStream metadataStream = metadataResource.openStream();
            XMLHelper.parse(metadataStream, null, new ContextualSAXHandler() {
                public void endElement(String uri, String localName, String qName)
                        throws SAXException {
                    if ("metadata/versioning/snapshot/timestamp".equals(getContext())) {
                        mavenMetadata.timestamp = getText();
                    }
                    if ("metadata/versioning/snapshot/buildNumber".equals(getContext())) {
                        mavenMetadata.buildNumber = getText();
                    }
                    if ("metadata/versioning/versions/version".equals(getContext())) {
                        mavenMetadata.versions.add(getText().trim());
                    }
                    super.endElement(uri, localName, qName);
                }
            }, null);
        } else {
            LOGGER.debug("maven-metadata not available: {}", metadataResource);
        }
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

    private boolean shouldResolveDependencyDescriptors() {
        return isUsepoms() && isM2compatible();
    }

    public boolean isUseMavenMetadata() {
        return useMavenMetadata;
    }

    public void setUseMavenMetadata(boolean useMavenMetadata) {
        this.useMavenMetadata = useMavenMetadata;
    }

    private boolean shouldUseMavenMetadata(String pattern) {
        return isUseMavenMetadata() && isM2compatible() && pattern.endsWith(M2_PATTERN);
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

    private static class MavenMetadata {
        public String timestamp;
        public String buildNumber;
        public List<String> versions = new ArrayList<String>();
    }

}
