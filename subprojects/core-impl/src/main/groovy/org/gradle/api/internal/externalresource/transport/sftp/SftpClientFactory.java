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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.jcraft.jsch.*;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@ThreadSafe
public class SftpClientFactory implements Stoppable {
    private SftpClientCreator sftpClientCreator = new SftpClientCreator();
    private final Object lock = new Object();
    private final ListMultimap<SftpHost, LockableSftpClient> clients = ArrayListMultimap.create();

    public LockableSftpClient createSftpClient(URI uri, PasswordCredentials credentials) throws IOException {
        synchronized (lock) {
            SftpHost sftpHost = new SftpHost(uri, credentials);
            return acquireClient(sftpHost);
        }
    }

    private LockableSftpClient acquireClient(SftpHost sftpHost) throws IOException {
        return clients.containsKey(sftpHost) ? reuseExistingOrCreateNewClient(sftpHost) : sftpClientCreator.createNewClient(sftpHost);
    }

    private LockableSftpClient reuseExistingOrCreateNewClient(SftpHost sftpHost) throws IOException {
        List<LockableSftpClient> clientsByHost = clients.get(sftpHost);
        if (clientsByHost.isEmpty()) {
            return sftpClientCreator.createNewClient(sftpHost);
        }
        return clientsByHost.remove(0);
    }

    private static class SftpClientCreator {
        private JSch jsch;

        public LockableSftpClient createNewClient(SftpHost sftpHost) throws IOException {
            try {
                Session session = createJsch().getSession(sftpHost.getUsername(), sftpHost.getHostname(), sftpHost.getPort());
                session.setPassword(sftpHost.getPassword());
                session.connect();
                Channel channel = session.openChannel("sftp");
                channel.connect();
                return new DefaultLockableSftpClient(sftpHost, (ChannelSftp) channel, session);
            } catch (JSchException e) {
                if (e.getMessage().equals("Auth fail")) {
                    throw new SftpException(String.format("Invalid credentials for SFTP server at sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()), e);
                }
                throw new SftpException(String.format("Could not connect to SFTP server at sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()), e);
            }
        }

        private JSch createJsch() {
            if (jsch == null) {
                jsch = new JSch();
                jsch.setHostKeyRepository(new HostKeyRepository() {
                    public int check(String host, byte[] key) {
                        return OK;
                    }

                    public void add(HostKey hostkey, UserInfo ui) {
                    }

                    public void remove(String host, String type) {
                    }

                    public void remove(String host, String type, byte[] key) {
                    }

                    public String getKnownHostsRepositoryID() {
                        return "allow-everything";
                    }

                    public HostKey[] getHostKey() {
                        throw new UnsupportedOperationException();
                    }

                    public HostKey[] getHostKey(String host, String type) {
                        return new HostKey[0];
                    }
                });
            }
            return jsch;
        }
    }

    public void releaseSftpClient(LockableSftpClient sftpClient) {
        synchronized (lock) {
            clients.put(sftpClient.getHost(), sftpClient);
        }
    }

    public void stop() {
        synchronized (lock) {
            try {
                CompositeStoppable stoppable = new CompositeStoppable();
                for (LockableSftpClient client : clients.values()) {
                    stoppable.add(client);
                }
                stoppable.stop();
            } finally {
                clients.clear();
            }
        }
    }

    private static class DefaultLockableSftpClient implements LockableSftpClient {
        final SftpHost host;
        final ChannelSftp channelSftp;
        final Session session;

        private DefaultLockableSftpClient(SftpHost host, ChannelSftp channelSftp, Session session) {
            this.host = host;
            this.channelSftp = channelSftp;
            this.session = session;
        }

        public void stop() {
            channelSftp.disconnect();
            session.disconnect();
        }

        public SftpHost getHost() {
            return host;
        }

        public ChannelSftp getSftpClient() {
            return channelSftp;
        }
    }
}
