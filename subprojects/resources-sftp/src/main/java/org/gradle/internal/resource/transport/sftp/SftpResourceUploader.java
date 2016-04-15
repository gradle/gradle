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
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.local.LocalResource;
import org.gradle.internal.resource.transfer.ExternalResourceUploader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class SftpResourceUploader implements ExternalResourceUploader {

    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceUploader(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
        this.sftpClientFactory = sftpClientFactory;
        this.credentials = credentials;
    }

    @Override
    public void upload(LocalResource resource, URI destination) throws IOException {
        LockableSftpClient client = sftpClientFactory.createSftpClient(destination, credentials);

        try {
            ChannelSftp channel = client.getSftpClient();
            ensureParentDirectoryExists(channel, destination);
            InputStream sourceStream = resource.open();
            try {
                channel.put(sourceStream, destination.getPath());
            } finally {
                sourceStream.close();
            }
        } catch (com.jcraft.jsch.SftpException e) {
            throw ResourceExceptions.putFailed(destination, e);
        } finally {
            sftpClientFactory.releaseSftpClient(client);
        }
    }

    private void ensureParentDirectoryExists(ChannelSftp channel, URI uri) {
        String parentPath = FilenameUtils.getFullPathNoEndSeparator(uri.getPath());
        if (parentPath.equals("/")) {
            return;
        }
        URI parent = uri.resolve(parentPath);

        try {
            channel.lstat(parentPath);
            return;
        } catch (com.jcraft.jsch.SftpException e) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw new ResourceException(parent, String.format("Could not lstat resource '%s'.", parent), e);
            }
        }
        ensureParentDirectoryExists(channel, parent);
        try {
            channel.mkdir(parentPath);
        } catch (com.jcraft.jsch.SftpException e) {
            throw new ResourceException(parent, String.format("Could not create resource '%s'.", parent), e);
        }
    }
}
