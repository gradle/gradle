/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.externalresource.transport.sftp;

import org.apache.sshd.client.SftpClient;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.internal.hash.HashValue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.apache.sshd.client.SftpClient.Attribute.AcModTime;
import static org.apache.sshd.client.SftpClient.Attribute.Size;
import static org.apache.sshd.client.SftpClient.Attributes;

public class SftpResourceAccessor implements ExternalResourceAccessor {

    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceAccessor(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
        this.sftpClientFactory = sftpClientFactory;
        this.credentials = credentials;
    }

    private URI toUri(String location) {
        URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            throw new ResourceException(String.format("Unable to create URI from string '%s' ", location), e);
        }
        return uri;
    }

    private ExternalResourceMetaData getMetaData(URI uri) throws IOException {
        SftpClient sftpClient = null;
        try {
            sftpClient = sftpClientFactory.createSftpClient(uri, credentials);
            Attributes attributes = sftpClient.lstat(uri.getPath());
            return attributes != null ? toMetaData(uri.toString(), attributes) : null;
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }

    private ExternalResourceMetaData toMetaData(String path, Attributes attributes) {
        long lastModified = -1;
        long contentLength = -1;

        if (attributes.flags.contains(AcModTime)) {
            lastModified = attributes.mtime * 1000;
        }
        if (attributes.flags.contains(Size)) {
            contentLength = attributes.size;
        }

        return new DefaultExternalResourceMetaData(path, lastModified, contentLength, null, null);
    }

    public ExternalResource getResource(String location) throws IOException {
        URI uri = toUri(location);
        ExternalResourceMetaData metaData = getMetaData(uri);

        return metaData != null ? new SftpResource(sftpClientFactory, metaData, uri, credentials) : null;
    }

    public HashValue getResourceSha1(String location) {
        return null;
    }

    public ExternalResourceMetaData getMetaData(String location) throws IOException {
        return getMetaData(toUri(location));
    }
}
