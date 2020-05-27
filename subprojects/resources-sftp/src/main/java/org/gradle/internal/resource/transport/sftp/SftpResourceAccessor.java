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
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.net.URI;

public class SftpResourceAccessor implements ExternalResourceAccessor {

    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceAccessor(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
        this.sftpClientFactory = sftpClientFactory;
        this.credentials = credentials;
    }

    @Override
    public ExternalResourceMetaData getMetaData(URI uri, boolean revalidate) {
        LockableSftpClient sftpClient = sftpClientFactory.createSftpClient(uri, credentials);
        try {
            SftpATTRS attributes = sftpClient.getSftpClient().lstat(uri.getPath());
            return attributes != null ? toMetaData(uri, attributes) : null;
        } catch (com.jcraft.jsch.SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw ResourceExceptions.getFailed(uri, e);
        } finally {
            sftpClientFactory.releaseSftpClient(sftpClient);
        }
    }

    private ExternalResourceMetaData toMetaData(URI uri, SftpATTRS attributes) {
        long lastModified = -1;
        long contentLength = -1;

        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            lastModified = (long)attributes.getMTime() * 1000;
        }
        if ((attributes.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_SIZE) != 0) {
            contentLength = attributes.getSize();
        }

        return new DefaultExternalResourceMetaData(uri, lastModified, contentLength);
    }

    @Override
    public ExternalResourceReadResponse openResource(URI location, boolean revalidate) {
        ExternalResourceMetaData metaData = getMetaData(location, revalidate);
        return metaData != null ? new SftpResource(sftpClientFactory, metaData, location, credentials) : null;
    }
}
