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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class MavenResolver extends RepositoryResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    private static final String M2_PER_MODULE_PATTERN = "[revision]/[artifact]-[revision](-[classifier]).[ext]";
    private static final String M2_PATTERN = "[organisation]/[module]/" + M2_PER_MODULE_PATTERN;

    private final String root;

    public MavenResolver(String name, String root, Repository repository) {
        setName(name);
        this.root = normaliseRoot(root);
        setIvyPatterns(Collections.singletonList(getWholePattern()));
        setArtifactPatterns(Collections.singletonList(getWholePattern()));

        setRepository(repository);

        setDescriptor(DESCRIPTOR_OPTIONAL);
        setM2compatible(true);
        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");
    }

    public void addArtifactUrl(String url) {
        String newArtifactPattern = url + M2_PATTERN;
        List<String> artifactPatternList = new ArrayList<String>(getArtifactPatterns());
        artifactPatternList.add(newArtifactPattern);
        setArtifactPatterns(artifactPatternList);
    }

    private String getWholePattern() {
        return root + M2_PATTERN;
    }

    private String normaliseRoot(String root) {
        if (!root.endsWith("/")) {
            return root + "/";
        } else {
            return root;
        }
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

    private ResolvedResource findSnapshotDescriptor(DependencyDescriptor dd, ResolveData data,
                                                    ModuleRevisionId mrid) {
        String rev = findUniqueSnapshotVersion(mrid);
        if (rev != null) {
            // here it would be nice to be able to store the resolved snapshot version, to avoid
            // having to follow the same process to download artifacts

            LOGGER.debug("[{}] {}", rev, mrid);

            // replace the revision token in file name with the resolved revision
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            return findResourceUsingPattern(mrid, pattern,
                    DefaultArtifact.newPomArtifact(
                            mrid, data.getDate()), getRMDParser(dd, data), data.getDate());
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

    private ResolvedResource findSnapshotArtifact(Artifact artifact, Date date, ModuleRevisionId mrid) {
        String rev = findUniqueSnapshotVersion(mrid);
        if (rev != null) {
            // replace the revision token in file name with the resolved revision
            String pattern = getWholePattern().replaceFirst("\\-\\[revision\\]", "-" + rev);
            return findResourceUsingPattern(mrid, pattern, artifact,
                    getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date);
        }
        return null;
    }

    private String findUniqueSnapshotVersion(ModuleRevisionId mrid) {
        try {
            String metadataLocation = IvyPatternHelper.substitute(root + "[organisation]/[module]/[revision]/maven-metadata.xml", mrid);
            Resource metadata = getRepository().getResource(metadataLocation);
            if (metadata.exists()) {
                final StringBuffer timestamp = new StringBuffer();
                final StringBuffer buildNumber = new StringBuffer();
                parseMavenMetadata(metadata, timestamp, buildNumber);
                if (timestamp.length() > 0) {
                    // we have found a timestamp, so this is a snapshot unique version
                    String rev = mrid.getRevision();
                    rev = rev.substring(0, rev.length() - "SNAPSHOT".length());
                    rev = rev + timestamp.toString() + "-" + buildNumber.toString();
                    return rev;
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
        return null;
    }

    private void parseMavenMetadata(Resource metadata, final StringBuffer timestamp, final StringBuffer buildNumer) throws IOException, SAXException, ParserConfigurationException {
        InputStream metadataStream = metadata.openStream();
        try {
            XMLHelper.parse(metadataStream, null, new ContextualSAXHandler() {
                public void endElement(String uri, String localName, String qName)
                        throws SAXException {
                    if ("metadata/versioning/snapshot/timestamp".equals(getContext())) {
                        timestamp.append(getText());
                    }
                    if ("metadata/versioning/snapshot/buildNumber".equals(getContext())) {
                        buildNumer.append(getText());
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
    }

    protected String getModuleDescriptorExtension() {
        return "pom";
    }

    protected ResolvedResource[] listResources(
            Repository repository, ModuleRevisionId mrid, String pattern, Artifact artifact) {
        List revs = listRevisionsWithMavenMetadata(
                repository, mrid.getModuleId().getAttributes());
        if (revs != null) {
            LOGGER.debug("found revs: {}", revs);
            List rres = new ArrayList();
            for (Iterator iter = revs.iterator(); iter.hasNext(); ) {
                String rev = (String) iter.next();
                String resolvedPattern = IvyPatternHelper.substitute(
                        pattern, ModuleRevisionId.newInstance(mrid, rev), artifact);
                try {
                    Resource res = repository.getResource(resolvedPattern);
                    if ((res != null) && res.exists()) {
                        rres.add(new ResolvedResource(res, rev));
                    }
                } catch (IOException e) {
                    LOGGER.warn("impossible to get resource from name listed by maven-metadata.xml: " + rres, e);
                }
            }
            return (ResolvedResource[]) rres.toArray(new ResolvedResource[rres.size()]);
        } else {
            // maven metadata not available or something went wrong, 
            // use default listing capability
            return super.listResources(repository, mrid, pattern, artifact);
        }
    }

    private List listRevisionsWithMavenMetadata(Repository repository, Map tokenValues) {
        String metadataLocation = IvyPatternHelper.substituteTokens(root + "[organisation]/[module]/maven-metadata.xml", tokenValues);
        return listRevisionsWithMavenMetadata(repository, metadataLocation);
    }

    private List listRevisionsWithMavenMetadata(Repository repository, String metadataLocation) {
        List revs = null;
        InputStream metadataStream = null;
        try {
            Resource metadata = repository.getResource(metadataLocation);
            if (metadata.exists()) {
                LOGGER.debug("listing revisions from maven-metadata: {}", metadata);
                final List metadataRevs = new ArrayList();
                metadataStream = metadata.openStream();
                XMLHelper.parse(metadataStream, null, new ContextualSAXHandler() {
                    public void endElement(String uri, String localName, String qName)
                            throws SAXException {
                        if ("metadata/versioning/versions/version".equals(getContext())) {
                            metadataRevs.add(getText().trim());
                        }
                        super.endElement(uri, localName, qName);
                    }
                }, null);
                revs = metadataRevs;
            } else {
                LOGGER.debug("maven-metadata not available: {}", metadata);
            }
        } catch (IOException e) {
            LOGGER.warn("impossible to access maven metadata file, ignored.", e);
        } catch (SAXException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        } catch (ParserConfigurationException e) {
            LOGGER.warn("impossible to parse maven metadata file, ignored.", e);
        } finally {
            if (metadataStream != null) {
                try {
                    metadataStream.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
        return revs;
    }

    protected void findTokenValues(Collection names, List patterns, Map tokenValues, String token) {
        if (IvyPatternHelper.REVISION_KEY.equals(token)) {
            List revs = listRevisionsWithMavenMetadata(getRepository(), tokenValues);
            if (revs != null) {
                names.addAll(filterNames(revs));
                return;
            }
        }
        super.findTokenValues(names, patterns, tokenValues, token);
    }

    public void dumpSettings() {
        super.dumpSettings();
        Message.debug("\t\troot: " + root);
        Message.debug("\t\tpattern: " + M2_PATTERN);
    }

}
