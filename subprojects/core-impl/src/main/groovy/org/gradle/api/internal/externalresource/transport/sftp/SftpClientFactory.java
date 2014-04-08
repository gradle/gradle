/*
 * Copyright 2014 the original author or authors.
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

import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.SftpClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.net.URI;

public class SftpClientFactory implements Stoppable {
    private SshClientManager sshClientManager = new DefaultSshClientManager();

    public SftpClient createSftpClient(URI uri, PasswordCredentials credentials) throws IOException {
        ClientSession session = null;
        try {
            SshClient sshClient = sshClientManager.getSshClient();
            session = connect(sshClient, uri, credentials);
            authenticate(session, uri, credentials);
            return new NonExistingFileHandlingSftpClient(session);
        } catch (IOException e) {
            if (session != null) {
                session.close(true);
            }
            throw e;
        }
    }

    private ClientSession connect(SshClient client, URI uri, PasswordCredentials credentials) throws IOException {
        ConnectFuture connectFuture = client.connect(credentials.getUsername(), uri.getHost(), uri.getPort()).awaitUninterruptibly();
        if (!connectFuture.isConnected()) {
            throw new SftpException(String.format("Could not connect to SFTP server at sftp://%s:%d", uri.getHost(), uri.getPort()));
        }
        return connectFuture.getSession();
    }

    private ClientSession authenticate(ClientSession session, URI uri, PasswordCredentials credentials) throws IOException {
        session.addPasswordIdentity(credentials.getPassword());
        AuthFuture auth = session.auth().awaitUninterruptibly();
        if (!auth.isSuccess()) {
            throw new SftpException(String.format("Invalid credentials for SFTP server at sftp://%s:%d", uri.getHost(), uri.getPort()));
        }
        return session;
    }

    public void stop() {
        sshClientManager.stopSshClient();
    }
}
