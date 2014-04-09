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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sshd.client.SftpClient;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceUploader;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.internal.Factory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class SftpResourceUploader implements ExternalResourceUploader {

    private final SftpClientFactory sftpClientFactory;
    private final PasswordCredentials credentials;

    public SftpResourceUploader(SftpClientFactory sftpClientFactory, PasswordCredentials credentials) {
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

    public void upload(Factory<InputStream> sourceFactory, Long contentLength, String destination) throws IOException {
        URI uri = toUri(destination);
        SftpClient client = sftpClientFactory.createSftpClient(uri, credentials);
        String path = uri.getPath();

        OutputStream outputStream = null;
        try {
            ensureParentDirectoryExists(client, path);
            outputStream = client.write(uri.getPath());
            InputStream sourceStream = sourceFactory.create();
            try {
                IOUtils.copyLarge(sourceStream, outputStream);
                outputStream.flush();
            } finally {
                sourceStream.close();
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            sftpClientFactory.releaseSftpClient(client);
        }
    }

    private void ensureParentDirectoryExists(SftpClient client, String path) throws IOException {
        String directory = FilenameUtils.getFullPathNoEndSeparator(path);

        if (!directory.equals("") && client.lstat(directory) == null) {
            ensureParentDirectoryExists(client, directory);
            client.mkdir(directory);
        }
    }
}
