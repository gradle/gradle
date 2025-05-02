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
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ThreadSafe
@ServiceScope(Scope.Global.class)
public class SftpClientFactory implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SftpClientFactory.class);

    private SftpClientCreator sftpClientCreator = new SftpClientCreator();
    private final Object lock = new Object();
    private final List<LockableSftpClient> allClients = new ArrayList<>();
    private final ListMultimap<SftpHost, LockableSftpClient> idleClients = ArrayListMultimap.create();

    public LockableSftpClient createSftpClient(URI uri, PasswordCredentials credentials) {
        synchronized (lock) {
            SftpHost sftpHost = new SftpHost(uri, credentials);
            return acquireClient(sftpHost);
        }
    }

    private LockableSftpClient acquireClient(SftpHost sftpHost) {
        return idleClients.containsKey(sftpHost) ? reuseExistingOrCreateNewClient(sftpHost) : createNewClient(sftpHost);
    }

    private LockableSftpClient reuseExistingOrCreateNewClient(SftpHost sftpHost) {
        List<LockableSftpClient> clientsByHost = idleClients.get(sftpHost);

        LockableSftpClient client = null;
        if (clientsByHost.isEmpty()) {
            LOGGER.debug("No existing sftp clients.  Creating a new one.");
            client = createNewClient(sftpHost);
        } else {
            client = clientsByHost.remove(0);
            if (!client.isConnected()) {
                LOGGER.info("Tried to reuse an existing sftp client, but unexpectedly found it disconnected.  Discarding and trying again.");
                discard(client);
                client = reuseExistingOrCreateNewClient(sftpHost);
            } else {
                LOGGER.debug("Reusing an existing sftp client.");
            }
        }

        return client;
    }

    private LockableSftpClient createNewClient(SftpHost sftpHost) {
        LockableSftpClient client = sftpClientCreator.createNewClient(sftpHost);
        allClients.add(client);
        return client;
    }

    private void discard(LockableSftpClient client) {
        try {
            client.stop();
        } finally {
            allClients.remove(client);
        }
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
                        @Override
                        public boolean isEnabled(int level) {
                            return true;
                        }
                        @Override
                        public void log(int level, String message) {
                            LOGGER.debug(message);
                        }
                    });
                }
                jsch.setHostKeyRepository(new HostKeyRepository() {
                    @Override
                    public int check(String host, byte[] key) {
                        return HostKeyRepository.OK;
                    }

                    @Override
                    public void add(HostKey hostkey, UserInfo ui) {
                    }

                    @Override
                    public void remove(String host, String type) {
                    }

                    @Override
                    public void remove(String host, String type, byte[] key) {
                    }

                    @Override
                    public String getKnownHostsRepositoryID() {
                        return "allow-everything";
                    }

                    @Override
                    public HostKey[] getHostKey() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
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
            idleClients.put(sftpClient.getHost(), sftpClient);
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            try {
                CompositeStoppable.stoppable(allClients).stop();
            } finally {
                allClients.clear();
                idleClients.clear();
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

        @Override
        public void stop() {
            channelSftp.disconnect();
            session.disconnect();
        }

        @Override
        public SftpHost getHost() {
            return host;
        }

        @Override
        public ChannelSftp getSftpClient() {
            return channelSftp;
        }

        @Override
        public boolean isConnected() {
            return channelSftp.isConnected();
        }
    }
}
