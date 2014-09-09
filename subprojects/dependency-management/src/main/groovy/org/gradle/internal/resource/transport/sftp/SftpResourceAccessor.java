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

package org.gradle.internal.resource.transport.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.PasswordCredentials;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;

import java.io.IOException;
import java.net.URI;

public class SftpResourceAccessor implements ExternalResourceAccessor {

    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceAccessor(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
        this.sftpClientFactory = sftpClientFactory;
        this.credentials = credentials;
    }

    public ExternalResourceMetaData getMetaData(URI uri) throws IOException {
        LockableSftpClient sftpClient = sftpClientFactory.createSftpClient(uri, credentials);
        try {
            SftpATTRS attributes = sftpClient.getSftpClient().lstat(uri.getPath());
            return attributes != null ? toMetaData(uri, attributes) : null;
        } catch (com.jcraft.jsch.SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw new SftpException(String.format("Could not get resource '%s'.", uri), e);
        } finally {
            sftpClientFactory.releaseSftpClient(sftpClient);
        }
    }

    private ExternalResourceMetaData toMetaData(URI uri, SftpATTRS attributes) {
        long lastModified = -1;
        long contentLength = -1;

        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            lastModified = attributes.getMTime() * 1000;
        }
        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_SIZE) != 0) {
            contentLength = attributes.getSize();
        }

        return new DefaultExternalResourceMetaData(uri, lastModified, contentLength, null, null);
    }

    public ExternalResource getResource(URI location) throws IOException {
        ExternalResourceMetaData metaData = getMetaData(location);
        return metaData != null ? new SftpResource(sftpClientFactory, metaData, location, credentials) : null;
    }

    public HashValue getResourceSha1(URI location) {
        return null;
    }
}
