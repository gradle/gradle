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
import com.google.common.net.HostAndPort;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@ThreadSafe
@ServiceScope(Scope.Global.class)
public class SftpClientFactory implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SftpClientFactory.class);

    private SftpClientCreator sftpClientCreator;
    private final Object lock = new Object();
    private final List<LockableSftpClient> allClients = new ArrayList<>();
    private final ListMultimap<SftpHost, LockableSftpClient> idleClients = ArrayListMultimap.create();
    private SftpClientFactory insecureProtocolFactory;

    public SftpClientFactory() {
        this(false);
    }

    private SftpClientFactory(boolean allowInsecureProtocol) {
        this.sftpClientCreator = new SftpClientCreator(allowInsecureProtocol);
    }

    public LockableSftpClient createSftpClient(URI uri, PasswordCredentials credentials) {
        synchronized (lock) {
            SftpHost sftpHost = new SftpHost(uri, credentials);
            return acquireClient(sftpHost);
        }
    }

    /**
     * A factory whose clients skip host key verification. It keeps its own pool, so that its clients are never handed to a repository that verifies host keys.
     */
    public SftpClientFactory allowingInsecureProtocol() {
        synchronized (lock) {
            if (insecureProtocolFactory == null) {
                insecureProtocolFactory = new SftpClientFactory(true);
            }
            return insecureProtocolFactory;
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
        private static final Path GLOBAL_KNOWN_HOSTS_FILE = globalKnownHostsFile();

        private final boolean allowInsecureProtocol;
        private JSch jsch;

        SftpClientCreator(boolean allowInsecureProtocol) {
            this.allowInsecureProtocol = allowInsecureProtocol;
        }

        public LockableSftpClient createNewClient(SftpHost sftpHost) {
            Session session = null;
            try {
                session = createJsch().getSession(sftpHost.getUsername(), HostAndPort.fromString(sftpHost.getHostname()).getHost(), sftpHost.getPort());
                session.setPassword(sftpHost.getPassword());
                if (!allowInsecureProtocol) {
                    session.setHostKeyRepository(loadKnownHosts());
                    session.setConfig("StrictHostKeyChecking", "yes");
                } else {
                    session.setHostKeyRepository(AcceptAnyHostKey.INSTANCE);
                }
                session.connect();
                Channel channel = session.openChannel("sftp");
                channel.connect();
                return new DefaultLockableSftpClient(sftpHost, (ChannelSftp) channel, session);
            } catch (JSchException e) {
                if (session != null) {
                    session.disconnect();
                }
                URI serverUri = URI.create(String.format("sftp://%s:%d", sftpHost.getHostname(), sftpHost.getPort()));
                HostKey presentedHostKey = session == null ? null : session.getHostKey();
                if (!allowInsecureProtocol && presentedHostKey != null) {
                    int status = check(session.getHostKeyRepository(), presentedHostKey);
                    if (status != HostKeyRepository.OK) {
                        throw untrustedHostKey(serverUri, sftpHost, presentedHostKey, status == HostKeyRepository.CHANGED, e);
                    }
                }
                if (e.getMessage().equals("Auth fail")) {
                    throw new ResourceException(serverUri, String.format("Password authentication not supported or invalid credentials for SFTP server at %s", serverUri), e);
                }
                throw new ResourceException(serverUri, String.format("Could not connect to SFTP server at %s", serverUri), e);
            }
        }

        /**
         * Re-read on every connection, so that a long-lived daemon sees keys the user added since it started.
         */
        private static HostKeyRepository loadKnownHosts() throws JSchException {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            for (Path file : Arrays.asList(userKnownHostsFile(), GLOBAL_KNOWN_HOSTS_FILE)) {
                if (Files.isRegularFile(file) && Files.isReadable(file)) {
                    try {
                        content.write(Files.readAllBytes(file));
                        content.write('\n');
                    } catch (IOException e) {
                        LOGGER.debug("Could not read SSH known hosts file {}", file, e);
                    }
                }
            }
            JSch holder = new JSch();
            holder.setKnownHosts(new ByteArrayInputStream(content.toByteArray()));
            return holder.getHostKeyRepository();
        }

        /**
         * OpenSSH's default {@code GlobalKnownHostsFile}.
         */
        private static Path globalKnownHostsFile() {
            if (OperatingSystem.current().isWindows()) {
                String programData = System.getenv("ProgramData");
                return Paths.get(programData != null ? programData : "C:\\ProgramData", "ssh", "ssh_known_hosts");
            }
            return Paths.get("/etc/ssh/ssh_known_hosts");
        }

        private static Path userKnownHostsFile() {
            return Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");
        }

        /**
         * JSch reports a rejected host key only through its exception message, so the key the server presented is checked again here.
         */
        private static int check(HostKeyRepository knownHosts, HostKey presented) {
            return knownHosts.check(presented.getHost(), Base64.getDecoder().decode(presented.getKey()));
        }

        private static ResourceException untrustedHostKey(URI serverUri, SftpHost sftpHost, HostKey presented, boolean changed, JSchException cause) {
            String reason = changed
                ? "its key does not match the one listed for this host in the SSH known hosts files. The server's key may have changed, or another server may be impersonating it"
                : "the host is not listed in the SSH known hosts files";
            return new ResourceException(serverUri, String.format(
                "Could not verify the identity of SFTP server at %s: %s. The server presented the %s key with fingerprint %s. "
                    + "Check this fingerprint with the server's administrator, then add the key to %s, for example with 'ssh-keyscan -p %d %s >> ~/.ssh/known_hosts'. "
                    + "Alternatively, allow insecure protocols for the repository. %s",
                serverUri, reason, presented.getType(), fingerprint(presented),
                userKnownHostsFile(), sftpHost.getPort(), HostAndPort.fromString(sftpHost.getHostname()).getHost(),
                Documentation.dslReference(UrlArtifactRepository.class, "allowInsecureProtocol").getConsultDocumentationMessage()
            ), cause);
        }

        /**
         * In the format {@code ssh-keygen -l} and OpenSSH prompts print, so that it can be compared with what the server's administrator has.
         */
        private static String fingerprint(HostKey hostKey) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(hostKey.getKey()));
                return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
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
            }
            return jsch;
        }
    }

    @NonNullApi
    private static class AcceptAnyHostKey implements HostKeyRepository {
        static final AcceptAnyHostKey INSTANCE = new AcceptAnyHostKey();

        @Override
        public int check(String host, byte[] key) {
            return HostKeyRepository.OK;
        }

        @Override
        public void add(HostKey hostkey, @Nullable UserInfo ui) {
        }

        @Override
        public void remove(String host, @Nullable String type) {
        }

        @Override
        public void remove(String host, @Nullable String type, @Nullable byte[] key) {
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
        public HostKey[] getHostKey(@Nullable String host, @Nullable String type) {
            return new HostKey[0];
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
                CompositeStoppable stoppable = CompositeStoppable.stoppable(allClients);
                if (insecureProtocolFactory != null) {
                    stoppable.add(insecureProtocolFactory);
                }
                stoppable.stop();
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
