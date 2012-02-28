/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.fixtures;

import com.sshtools.daemon.SshServer;
import com.sshtools.daemon.configuration.XmlServerConfigurationContext;
import org.apache.commons.io.FileUtils;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.xml.parsers.ParserConfigurationException;

import com.sshtools.daemon.session.SessionChannelFactory;
import com.sshtools.j2ssh.configuration.ConfigurationException;
import com.sshtools.j2ssh.configuration.ConfigurationLoader;
import com.sshtools.j2ssh.connection.ConnectionProtocol;
import org.xml.sax.SAXException
import groovy.xml.MarkupBuilder
import static org.junit.Assert.*
import org.gradle.util.TestFile

class SFTPServer extends ExternalResource {
    final String username;
    final int port;
    final String hostAddress;

    private TemporaryFolder baseDir = new TemporaryFolder();
    private TemporaryFolder configDir = new TemporaryFolder();
    private SshServer server;
    private File userHome

    public SFTPServer(String username, int port, String hostAddress) {
        this.username = username;
        this.port = port;
        this.hostAddress = hostAddress;
    }

    protected void before() throws Throwable {
        // Setup the temporary folder and copy configuration files
        baseDir.create();
        configDir.create();
        setupConfiguration();

        // Run it in a separate thread
        Executors.newSingleThreadExecutor().submit(new Callable<Object>() {
            public Object call() throws Exception {
                start();
                return null;
            }
        });
    }

    protected void after() {
        try {
            stop();
        } catch (Throwable e) {

        }

        try {
            configDir.delete();
        } catch (Throwable e) {

        }

        try {
            baseDir.delete();
        } catch (Throwable e) {
        }
    }

    private void setupConfiguration() throws IOException, SAXException, ParserConfigurationException {
        createServerConfig();
        createPlatformConfig();
        copyDsaKey();
        setupHomeDir();
        configureServer();
    }

    void createPlatformConfig() {
        new File(configDir.getRoot(), "platform.xml").withWriter { writer ->
            def xml = new MarkupBuilder(writer)
            xml.PlatformConfiguration() {
                NativeProcessProvider("com.sshtools.daemon.platform.UnsupportedShellProcessProvider")
                NativeAuthenticationProvider("org.gradle.integtests.fixtures.SshDummyAuthenticationProvider")
                NativeFileSystemProvider("com.sshtools.daemon.vfs.VirtualFileSystem")
                VFSRoot(path: baseDir.getRoot());
            }
        }
    }

    void createServerConfig() {
        String keyFilePath = new File(configDir.getRoot(), "test-dsa.key").absolutePath
        new File(configDir.getRoot(), "server.xml").withWriter { writer ->
            def xml = new MarkupBuilder(writer)
            xml.doubleQuotes = true
            xml.ServerConfiguration() {
                ServerHostKey(PrivateKeyFile: "${keyFilePath}")
                Port(port)
                ListenAddress(getHostAddress())
                MaxConnections(3)
                AllowedAuthentication("password")
                Subsystem(Name: "sftp", Type: "class", Provider: "com.sshtools.daemon.sftp.SftpSubsystemServer")
            }
        }
    }

    private void setupHomeDir() {
        File homeBase = baseDir.newFolder("home");
        userHome = new File(homeBase, username);
        userHome.mkdirs();
    }

    private void configureServer() throws ConfigurationException {
            String configBase = configDir.root.absolutePath.replace('\\', '/') + '/';
            XmlServerConfigurationContext context = new XmlServerConfigurationContext();
            context.setServerConfigurationResource("$configBase/server.xml");
            context.setPlatformConfigurationResource("$configBase/platform.xml");
            ConfigurationLoader.initialize(false, context);
        }


    private void copyDsaKey() throws IOException {
        URL fileUrl = ClassLoader.getSystemResource("sshd-config/test-dsa.key");
        FileUtils.copyURLToFile(fileUrl, new File(configDir.getRoot(), "test-dsa.key"));
    }

    private void start() throws IOException {

        server = new SshServer() {
            public void shutdown(String msg) {
            }

            @Override
            protected void configureServices(ConnectionProtocol connectionProtocol) throws IOException {
                connectionProtocol.addChannelFactory(SessionChannelFactory.SESSION_CHANNEL, new SessionChannelFactory());
            }

            protected boolean isAcceptConnectionFrom(Socket socket) {
                return true;
            }
        };

        server.startServer();
    }

    private void stop() throws ConfigurationException, UnknownHostException, IOException {
        server?.stopServer();
    }

    boolean hasFile(String filePathToCheck) {
        new File(userHome, filePathToCheck).exists()
    }

    TestFile file(String expectedPath) {
        new TestFile(new File(userHome, expectedPath))
    }
}

