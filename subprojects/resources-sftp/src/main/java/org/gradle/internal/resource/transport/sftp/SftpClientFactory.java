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

package org.gradle.internal.resource.transport.sftp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.jcraft.jsch.*;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

@ThreadSafe
public class SftpClientFactory implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SftpClientFactory.class);

    private SftpClientCreator sftpClientCreator = new SftpClientCreator();
    private final Object lock = new Object();
    private final ListMultimap<SftpHost, LockableSftpClient> clients = ArrayListMultimap.create();

    public LockableSftpClient createSftpClient(URI uri, PasswordCredentials credentials) {
        synchronized (lock) {
            SftpHost sftpHost = new SftpHost(uri, credentials);
            return acquireClient(sftpHost);
        }
    }

    private LockableSftpClient acquireClient(SftpHost sftpHost) {
        return clients.containsKey(sftpHost) ? reuseExistingOrCreateNewClient(sftpHost) : sftpClientCreator.createNewClient(sftpHost);
    }

    private LockableSftpClient reuseExistingOrCreateNewClient(SftpHost sftpHost) {
        List<LockableSftpClient> clientsByHost = clients.get(sftpHost);
        if (clientsByHost.isEmpty()) {
            return sftpClientCreator.createNewClient(sftpHost);
        }
        return clientsByHost.remove(0);
    }

    private static class SftpClientCreator {
        private JSch jsch;

        public LockableSftpClient createNewClient(SftpHost sftpHost) {
            try {
                Session session = createJsch().getSession(sftpHost.getUsername(), sftpHost.getHostname(), sftpHost.getPort());
                session.setPassword(sftpHost.getPassword());
                session.connect();
                Channel channel = session.openChannel("sftp");
                channel.connect();
                return new DefaultLockableSftpClient(sftpHost, (ChannelSftp) channel, session);
            } catch (JSchException e) {
                URI serverUri = URI.create(String.format("sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()));
                if (e.getMessage().equals("Auth fail")) {
                    throw new ResourceException(serverUri, String.format("Password authentication not supported or invalid credentials for SFTP server at %s", serverUri), e);
                }
                throw new ResourceException(serverUri, String.format("Could not connect to SFTP server at %s", serverUri), e);
            }
        }

        private JSch createJsch() {
            if (jsch == null) {
                JSch.setConfig("PreferredAuthentications", "password");
                JSch.setConfig("MaxAuthTries", "1");
                jsch = new JSch();
                if(LOGGER.isDebugEnabled()) {
                    JSch.setLogger(new com.jcraft.jsch.Logger() {
                        public boolean isEnabled(int level) {
                            return true;
                        }
                        public void log(int level, String message) {
                            LOGGER.debug(message);
                        }
                    });
                }
                jsch.setHostKeyRepository(new HostKeyRepository() {
                    public int check(String host, byte[] key) {
                        return HostKeyRepository.OK;
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
