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

import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.InputStream;
import java.net.URI;

public class SftpResource implements ExternalResourceReadResponse {

    private final SftpClientFactory clientFactory;
    private final ExternalResourceMetaData metaData;
    private final URI uri;
    private final PasswordCredentials credentials;

    private LockableSftpClient client;

    public SftpResource(SftpClientFactory clientFactory, ExternalResourceMetaData metaData, URI uri, PasswordCredentials credentials) {
        this.clientFactory = clientFactory;
        this.metaData = metaData;
        this.uri = uri;
        this.credentials = credentials;
    }

    @Override
    public InputStream openStream() {
        client = clientFactory.createSftpClient(uri, credentials);
        try {
            return client.getSftpClient().get(uri.getPath());
        } catch (com.jcraft.jsch.SftpException e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    public URI getURI() {
        return uri;
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    @Override
    public void close() {
        clientFactory.releaseSftpClient(client);
    }
}
