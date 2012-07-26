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
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.ContextualSAXHandler;
import org.apache.ivy.util.XMLHelper;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenVersionLister implements VersionLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenVersionLister.class);

    private final ExternalResourceRepository repository;
    private final String root;

    public MavenVersionLister(ExternalResourceRepository repository, String root) {
        this.repository = repository;
        this.root = root;
    }

    public VersionList getVersionList(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) throws ResourceException, ResourceNotFoundException {
        try {
            if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
                return new DefaultVersionList(Collections.<String>emptyList());
            }
            return new DefaultVersionList(listRevisionsWithMavenMetadata(moduleRevisionId.getModuleId().getAttributes()));
        } catch (IOException e) {
            throw new ResourceException("Unable to load Maven Metadata file", e);
        } catch (SAXException e) {
            throw new ResourceException("Unable to parse Maven Metadata file", e);
        } catch (ParserConfigurationException e) {
            throw new ResourceException("Unable to parse Maven Metadata file", e);
        }
    }

    private List<String> listRevisionsWithMavenMetadata(Map tokenValues) throws IOException, SAXException, ParserConfigurationException, ResourceNotFoundException {
        String metadataLocation = IvyPatternHelper.substituteTokens(root + "[organisation]/[module]/maven-metadata.xml", tokenValues);
        final ExternalResource resource = repository.getResource(metadataLocation);
        if (resource == null) {
            throw new ResourceNotFoundException(String.format("maven-metadata not available: {}", metadataLocation));
        }
        try {
            return parseMavenMetadataVersions(resource);
        } finally {
            if (resource != null) {
                resource.close();
            }
        }
    }

    private List<String> parseMavenMetadataVersions(Resource metadataResource) throws IOException, SAXException, ParserConfigurationException {

        final List<String> metadataVersions = new ArrayList<String>();
        LOGGER.debug("parsing maven-metadata: {}", metadataResource);
        InputStream metadataStream = metadataResource.openStream();
        XMLHelper.parse(metadataStream, null, new ContextualSAXHandler() {
            public void endElement(String uri, String localName, String qName)
                    throws SAXException {
                if ("metadata/versioning/versions/version".equals(getContext())) {
                    metadataVersions.add(getText().trim());
                }
                super.endElement(uri, localName, qName);
            }
        }, null);
        return metadataVersions;
    }
}