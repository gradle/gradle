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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.util.ContextualSAXHandler;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.XMLHelper;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

public class MavenResolver extends RepositoryResolver implements PatternBasedResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    private static final String M2_PER_MODULE_PATTERN = "[revision]/[artifact]-[revision](-[classifier]).[ext]";
    private static final String M2_PATTERN = "[organisation]/[module]/" + M2_PER_MODULE_PATTERN;

    private final RepositoryTransport transport;
    private final String root;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport) {
        setName(name);
        setRepository(transport.getIvyRepository());
        transport.configureCacheManager(this);

        this.transport = transport;
        this.root = transport.convertToPath(rootUri);
        addIvyPattern(getWholePattern());
        addArtifactPattern(getWholePattern());

        setDescriptor(DESCRIPTOR_OPTIONAL);
        setM2compatible(true);
        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");
    }
    
    public void addArtifactLocation(URI baseUri, String pattern) {
        if (pattern != null && pattern.length() > 0) {
            throw new IllegalArgumentException("Maven Resolver does not support patterns other than the standard M2 pattern");
        }

        String newArtifactPattern = transport.convertToPath(baseUri) + M2_PATTERN;
        addArtifactPattern(newArtifactPattern);
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        throw new UnsupportedOperationException("Cannot have multiple descriptor urls for MavenResolver");        
    }

    private String getWholePattern() {
        return root + M2_PATTERN;
    }

    private String getMavenMetadataPattern() {
        return root + "[organisation]/[module]/[revision]/maven-metadata.xml";
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        ModuleRevisionId moduleRevisionId = convertM2IdForResourceSearch(dd.getDependencyRevisionId());

        if (moduleRevisionId.getRevision().endsWith("SNAPSHOT")) {
            ResolvedResource resolvedResource = findSnapshotDescriptor(dd, data, moduleRevisionId);
            if (resolvedResource != null) {
                return resolvedResource;
            }
        }

        Artifact pomArtifact = DefaultArtifact.newPomArtifact(moduleRevisionId, data.getDate());
        ResourceMDParser parser = getRMDParser(dd, data);
        return findResourceUsingPatterns(moduleRevisionId, getIvyPatterns(), pomArtifact, parser, data.getDate());
    }

    private ResolvedResource findSnapshotDescriptor(DependencyDescriptor dd, ResolveData data, ModuleRevisionId moduleRevisionId) {
        String rev = findUniqueSnapshotVersion(moduleRevisionId);
        if (rev != null) {
            // here it would be nice to be able to store the resolved snapshot version, to avoid
            // having to follow the same process to download artifacts

            LOGGER.debug("[{}] {}", rev, moduleRevisionId);

            // replace the revision token in file name with the resolved revision
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            return findResourceUsingPattern(moduleRevisionId, pattern,
                    DefaultArtifact.newPomArtifact(
                            moduleRevisionId, data.getDate()), getRMDParser(dd, data), data.getDate());
        }
        return null;
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        ModuleRevisionId moduleRevisionId = convertM2IdForResourceSearch(artifact.getModuleRevisionId());
        if (moduleRevisionId.getRevision().endsWith("SNAPSHOT")) {
            ResolvedResource resolvedResource = findSnapshotArtifact(artifact, date, moduleRevisionId);
            if (resolvedResource != null) {
                return resolvedResource;
            }
        }
        ResourceMDParser parser = getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId());
        return findResourceUsingPatterns(moduleRevisionId, getArtifactPatterns(), artifact, parser, date);
    }

    private ResolvedResource findSnapshotArtifact(Artifact artifact, Date date, ModuleRevisionId moduleRevisionId) {
        String rev = findUniqueSnapshotVersion(moduleRevisionId);
        if (rev != null) {
            // replace the revision token in file name with the resolved revision
            // TODO:DAZ We're not using all available artifact patterns here, only the "main" pattern. This means that snapshot artifacts will not be resolved in additional artifact urls.
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            return findResourceUsingPattern(moduleRevisionId, pattern, artifact,
                    getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date);
        }
        return null;
    }

    private String findUniqueSnapshotVersion(ModuleRevisionId moduleRevisionId) {
        String metadataLocation = IvyPatternHelper.substitute(getMavenMetadataPattern(), moduleRevisionId);
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation, getRepository());
        
        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String rev = moduleRevisionId.getRevision();
            rev = rev.substring(0, rev.length() - "SNAPSHOT".length());
            rev = rev + mavenMetadata.timestamp + "-" + mavenMetadata.buildNumber;
            return rev;
        }
        return null;
    }

    protected String getModuleDescriptorExtension() {
        return "pom";
    }

    protected ResolvedResource[] listResources(Repository repository, ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) {
        List<String> revisions = listRevisionsWithMavenMetadata(repository, moduleRevisionId.getModuleId().getAttributes());
        if (revisions != null) {
            LOGGER.debug("Found revisions: {}", revisions);
            List<ResolvedResource> resources = new ArrayList<ResolvedResource>();
            for (String revision : revisions) {
                String resolvedPattern = IvyPatternHelper.substitute(
                        pattern, ModuleRevisionId.newInstance(moduleRevisionId, revision), artifact);
                try {
                    Resource res = repository.getResource(resolvedPattern);
                    if ((res != null) && res.exists()) {
                        resources.add(new ResolvedResource(res, revision));
                    }
                } catch (IOException e) {
                    LOGGER.warn("impossible to get resource from name listed by maven-metadata.xml: " + resources, e);
                }
            }
            return resources.toArray(new ResolvedResource[resources.size()]);
        } else {
            // maven metadata not available or something went wrong,
            // use default listing capability
            return super.listResources(repository, moduleRevisionId, pattern, artifact);
        }
    }

    protected void findTokenValues(Collection names, List patterns, Map tokenValues, String token) {
        if (IvyPatternHelper.REVISION_KEY.equals(token)) {
            List<String> revisions = listRevisionsWithMavenMetadata(getRepository(), tokenValues);
            if (revisions != null) {
                names.addAll(filterNames(revisions));
                return;
            }
        }
        super.findTokenValues(names, patterns, tokenValues, token);
    }

    private List<String> listRevisionsWithMavenMetadata(Repository repository, Map tokenValues) {
        String metadataLocation = IvyPatternHelper.substituteTokens(root + "[organisation]/[module]/maven-metadata.xml", tokenValues);
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation, repository);
        return mavenMetadata.versions.isEmpty() ? null : mavenMetadata.versions;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation, Repository repository) {
        final MavenMetadata mavenMetadata = new MavenMetadata();

        try {
            Resource metadata = repository.getResource(metadataLocation);
            if (metadata.exists()) {
                LOGGER.debug("parsing maven-metadata: {}", metadata);
                InputStream metadataStream = metadata.openStream();
                try {
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
                                mavenMetadata.versions.add((getText().trim()));
                            }
                            super.endElement(uri, localName, qName);
                        }
                    }, null);
                } finally {
                    try {
                        metadataStream.close();
                    } catch (IOException e) {
                        // ignored
                    }
                }
            } else {
                LOGGER.debug("maven-metadata not available: {}", metadata);
            }
        } catch (IOException e) {
            LOGGER.warn("impossible to access maven metadata file, ignored.", e);
        } catch (SAXException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        } catch (ParserConfigurationException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        }
        return mavenMetadata;
    }

    public void dumpSettings() {
        super.dumpSettings();
        Message.debug("\t\troot: " + root);
        Message.debug("\t\tpattern: " + M2_PATTERN);
    }

    private static class MavenMetadata {
        public String timestamp;
        public String buildNumber;
        public List<String> versions = new ArrayList<String>();
    }

}
