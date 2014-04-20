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

import net.jcip.annotations.ThreadSafe;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.SftpClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@ThreadSafe
public class SftpClientFactory implements Stoppable {
    private final Logger logger = Logging.getLogger(SftpClientFactory.class);
    private SshClientManager sshClientManager = new DefaultSshClientManager();
    private final Map<SftpHost, List<LockableSftpClient>> clients = Collections.synchronizedMap(new HashMap<SftpHost, List<LockableSftpClient>>());

    public SftpClient createSftpClient(URI uri, PasswordCredentials credentials) throws IOException {
        synchronized(clients) {
            SftpHost sftpHost = new SftpHost(uri, credentials);
            LockableSftpClient sftpClient = acquireClient(sftpHost);
            sftpClient.lock();
            return sftpClient;
        }
    }

    private LockableSftpClient acquireClient(SftpHost sftpHost) throws IOException {
        return clients.containsKey(sftpHost) ? reuseExistingOrCreateNewClient(sftpHost) : createAndStoreNewClient(sftpHost);
    }

    private LockableSftpClient reuseExistingOrCreateNewClient(SftpHost sftpHost) throws IOException {
        List<LockableSftpClient> clientsByHost = clients.get(sftpHost);

        for(LockableSftpClient client : clientsByHost) {
            if(!client.isLocked()) {
                return client;
            }
        }

        LockableSftpClient client = createNewClient(sftpHost);
        clientsByHost.add(client);
        return client;
    }

    private LockableSftpClient createAndStoreNewClient(SftpHost sftpHost) throws IOException {
        LockableSftpClient client = createNewClient(sftpHost);
        List<LockableSftpClient> clientsByHost = new ArrayList<LockableSftpClient>();
        clientsByHost.add(client);
        clients.put(sftpHost, clientsByHost);
        return client;
    }

    private LockableSftpClient createNewClient(SftpHost sftpHost) throws IOException {
        ClientSession session = null;
        try {
            SshClient sshClient = sshClientManager.getSshClient();
            session = connect(sshClient, sftpHost);
            authenticate(session, sftpHost);
            return new NonExistingFileHandlingSftpClient(session);
        } catch (IOException e) {
            if (session != null) {
                session.close(true);
            }
            throw e;
        }
    }

    public void releaseSftpClient(SftpClient sftpClient) {
        synchronized(clients) {
            if(sftpClient != null && sftpClient instanceof LockableSftpClient) {
                ((LockableSftpClient)sftpClient).unlock();
            }
        }
    }

    private ClientSession connect(SshClient client, SftpHost sftpHost) throws IOException {
        ConnectFuture connectFuture = client.connect(sftpHost.getUsername(), sftpHost.getHostname(), sftpHost.getPort()).awaitUninterruptibly();
        if (!connectFuture.isConnected()) {
            throw new SftpException(String.format("Could not connect to SFTP server at sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()));
        }
        return connectFuture.getSession();
    }

    private ClientSession authenticate(ClientSession session, SftpHost sftpHost) throws IOException {
        session.addPasswordIdentity(sftpHost.getPassword());
        AuthFuture auth = session.auth().awaitUninterruptibly();
        if (!auth.isSuccess()) {
            throw new SftpException(String.format("Invalid credentials for SFTP server at sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()));
        }
        return session;
    }

    public void stop() {
        synchronized(clients) {
            for(List<LockableSftpClient> allSftpClients : clients.values()) {
                for(LockableSftpClient client : allSftpClients) {
                    try {
                        client.close();
                    } catch(NullPointerException e) {
                        // SSHD client sometimes throws a NPE on close - ignore for now
                        // TODO: Ben SSHD project needs to address this issue - https://issues.apache.org/jira/browse/SSHD-311
                        logger.warn("Failed to close SFTP client " + client);
                    } catch(IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
        }

        sshClientManager.stopSshClient();
    }

    public static class SftpHost {
        private final String hostname;
        private final int port;
        private final String username;
        private final String password;

        private SftpHost(URI uri, PasswordCredentials credentials) {
            hostname = uri.getHost();
            port = uri.getPort();
            username = credentials.getUsername();
            password = credentials.getPassword();
        }

        private String getHostname() {
            return hostname;
        }

        private int getPort() {
            return port;
        }

        private String getUsername() {
            return username;
        }

        private String getPassword() {
            return password;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SftpHost sftpHost = (SftpHost) o;

            if (port != sftpHost.port) {
                return false;
            }
            if (!hostname.equals(sftpHost.hostname)) {
                return false;
            }
            if (password != null ? !password.equals(sftpHost.password) : sftpHost.password != null) {
                return false;
            }
            if (username != null ? !username.equals(sftpHost.username) : sftpHost.username != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = hostname.hashCode();
            result = 31 * result + port;
            result = 31 * result + (username != null ? username.hashCode() : 0);
            result = 31 * result + (password != null ? password.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s:%d (Username: %s)", hostname, port, username);
        }
    }
}
