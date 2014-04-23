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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
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
        LockableSftpClient sftpClient = sftpClientFactory.createSftpClient(uri, credentials);
        try {
            SftpATTRS attributes = sftpClient.getSftpClient().lstat(uri.getPath());
            return attributes != null ? toMetaData(uri.toString(), attributes) : null;
        } catch (com.jcraft.jsch.SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw new SftpException(String.format("Could not get resource '%s'.", uri), e);
        } finally {
            sftpClientFactory.releaseSftpClient(sftpClient);
        }
    }

    private ExternalResourceMetaData toMetaData(String path, SftpATTRS attributes) {
        long lastModified = -1;
        long contentLength = -1;

        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            lastModified = attributes.getMTime() * 1000;
        }
        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_SIZE) != 0) {
            contentLength = attributes.getSize();
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
