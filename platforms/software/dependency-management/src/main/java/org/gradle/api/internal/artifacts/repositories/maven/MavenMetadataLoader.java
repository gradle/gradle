/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.maven;

import org.apache.ivy.util.ContextualSAXHandler;
import org.apache.ivy.util.XMLHelper;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor.DefaultResourceFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

public class MavenMetadataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenMetadataLoader.class);
    private final CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor;
    private final FileStore<String> resourcesFileStore;

    public MavenMetadataLoader(CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor, FileStore<String> resourcesFileStore) {
        this.cacheAwareExternalResourceAccessor = cacheAwareExternalResourceAccessor;
        this.resourcesFileStore = resourcesFileStore;
    }

    public MavenMetadata load(ExternalResourceName metadataLocation) throws ResourceException {
        MavenMetadata metadata = new MavenMetadata();
        try {
            parseMavenMetadataInfo(metadataLocation, metadata);
        } catch (MissingResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ResourceException(metadataLocation.getUri(), String.format("Unable to load Maven meta-data from %s.", metadataLocation), e);
        }
        return metadata;
    }

    private void parseMavenMetadataInfo(final ExternalResourceName metadataLocation, final MavenMetadata metadata) throws IOException {
        ExternalResource resource = cacheAwareExternalResourceAccessor.getResource(metadataLocation, null, new DefaultResourceFileStore<String>(resourcesFileStore) {
            @Override
            protected String computeKey() {
                return metadataLocation.toString();
            }
        }, null);
        if (resource == null) {
            throw new MissingResourceException(metadataLocation.getUri(), String.format("Maven meta-data not available at %s", metadataLocation));
        }
        parseMavenMetadataInto(resource, metadata);
    }

    private void parseMavenMetadataInto(ExternalResource metadataResource, final MavenMetadata mavenMetadata) {
        LOGGER.debug("parsing maven-metadata: {}", metadataResource);
        metadataResource.withContent(new ErroringAction<InputStream>() {
            @Override
            public void doExecute(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
                XMLHelper.parse(inputStream, null, new ContextualSAXHandler() {
                    @Override
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
            }
        });
    }
}
