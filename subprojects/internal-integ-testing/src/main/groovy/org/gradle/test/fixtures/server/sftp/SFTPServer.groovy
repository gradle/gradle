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

package org.gradle.test.fixtures.server.sftp

import org.apache.commons.io.FileUtils
import org.apache.sshd.SshServer
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.Session
import org.apache.sshd.common.file.FileSystemView
import org.apache.sshd.common.file.SshFile
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemView
import org.apache.sshd.server.Command
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.AvailablePortFinder
import org.junit.rules.ExternalResource

import java.security.PublicKey

class SFTPServer extends ExternalResource {
    private final String hostAddress
    private int port

    private final TestDirectoryProvider testDirectoryProvider
    private TestFile baseDir
    private TestFile configDir
    private SshServer sshd

    FileRequestLogger fileRequestLogger = new FileRequestLogger()

    List<FileRequestListener> fileRequestListeners = [fileRequestLogger]

    SftpSubsystem sftpSubsystem = new SftpSubsystem()

    public SFTPServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
        this.hostAddress = "127.0.0.1"
    }

    protected void before() throws Throwable {
        baseDir = testDirectoryProvider.getTestDirectory().createDir("sshd/files")
        configDir = testDirectoryProvider.getTestDirectory().createDir("sshd/config")

        def portFinder = AvailablePortFinder.createPrivate()
        port = portFinder.nextAvailable
        sshd = setupConfiguredTestSshd();
        sshd.start();
    }

    protected void after() {
        stop()
    }

    public stop() {
        sshd?.stop()
    }

    private SshServer setupConfiguredTestSshd() {
        //copy dsa key to config directory
        URL fileUrl = ClassLoader.getSystemResource("sshd-config/test-dsa.key");
        FileUtils.copyURLToFile(fileUrl, new File(configDir, "test-dsa.key"));

        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(port);
        sshServer.setFileSystemFactory(new TestVirtualFileSystemFactory());
        sshServer.setSubsystemFactories(Arrays.<NamedFactory<Command>> asList(new SftpSubsystem.Factory() {
            Command create() {
                sftpSubsystem
            }
        }));
        sshServer.setCommandFactory(new ScpCommandFactory());
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("${configDir}/test-dsa.key"));
        sshServer.setPasswordAuthenticator(new DummyPasswordAuthenticator());
        sshServer.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            boolean authenticate(String username, PublicKey key, ServerSession session) {
                return true
            }
        });
        return sshServer;
    }

    boolean hasFile(String filePathToCheck) {
        new File(baseDir, filePathToCheck).exists()
    }

    TestFile file(String expectedPath) {
        new TestFile(new File(baseDir, expectedPath))
    }

    static class DummyPasswordAuthenticator implements PasswordAuthenticator {
        // every combination where username == password is accepted
        boolean authenticate(String username, String password, ServerSession session) {
            return username && password && username == password;
        }
    }

    static interface FileRequestListener {
        void fileRequested(String directory, String file)
    }

    static class FileRequestLogger implements FileRequestListener {
        def fileRequests = [] as Set

        void fileRequested(String directory, String file) {
            if (file.endsWith("xml") || file.endsWith(".jar")) {
                fileRequests << file - '/'
            }
        }
    }

    class TestVirtualFileSystemFactory extends VirtualFileSystemFactory {
        /**
         * Create the appropriate user file system view.
         */
        public FileSystemView createFileSystemView(Session session) {
            return new TestVirtualFileSystemView(baseDir.absolutePath, session.getUsername(), fileRequestListeners);
        }
    }

    class TestVirtualFileSystemView extends VirtualFileSystemView {

        List<FileRequestListener> fileRequestListeners

        /**
         * Constructor - internal do not use directly, use {@link NativeFileSystemFactory} instead
         */
        public TestVirtualFileSystemView(String rootPath, String userName, List<FileRequestListener> fileRequestListeners) {
            super(userName, rootPath);
            if (!rootPath) {
                throw new IllegalArgumentException("rootPath must be set");
            }

            if (!userName) {
                throw new IllegalArgumentException("user can not be null");
            }

            this.fileRequestListeners = fileRequestListeners;
        }

        protected SshFile getFile(String dir, String file) {
            fileRequestListeners*.fileRequested(dir, file);
            return super.getFile(dir, file);
        }
    }

}


