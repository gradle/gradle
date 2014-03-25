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
import org.apache.sshd.server.*
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.filesystem.NativeFileSystemFactory
import org.apache.sshd.server.filesystem.NativeSshFile
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.AvailablePortFinder
import org.junit.rules.ExternalResource

import java.security.PublicKey

class SFTPServer extends ExternalResource {
    final String hostAddress;
    int port

    private final TestDirectoryProvider testDirectoryProvider
    private TestFile baseDir
    private TestFile configDir

    private SshServer sshd;

    def fileRequests = [] as Set

    public SFTPServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
        def portFinder = AvailablePortFinder.createPrivate()
        port = portFinder.nextAvailable
        this.hostAddress = "127.0.0.1"
    }

    protected void before() throws Throwable {
        baseDir = testDirectoryProvider.getTestDirectory().createDir("sshd/files")
        configDir = testDirectoryProvider.getTestDirectory().createDir("sshd/config")

        sshd = setupConfiguredTestSshd();
        sshd.start();
    }

    protected void after() {
        sshd?.stop()
    }

    private SshServer setupConfiguredTestSshd() {
        //copy dsa key to config directory
        URL fileUrl = ClassLoader.getSystemResource("sshd-config/test-dsa.key");
        FileUtils.copyURLToFile(fileUrl, new File(configDir, "test-dsa.key"));

        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(port);
        sshServer.setFileSystemFactory(new TestNativeFileSystemFactory(baseDir.absolutePath, new FileRequestLogger() {
            void logRequest(String message) {
                fileRequests << message;
            }
        }));
        sshServer.setSubsystemFactories(Arrays.<NamedFactory<Command>> asList(new SftpSubsystem.Factory()));
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

    public Set<String> getFileRequests() {
        return fileRequests
    }

    public void clearRequests() {
        fileRequests.clear();
    }

    static class DummyPasswordAuthenticator implements PasswordAuthenticator {
        // every combination where username == password is accepted
        boolean authenticate(String username, String password, ServerSession session) {
            return username && password && username == password;
        }
    }

    static abstract class FileRequestLogger {
        abstract void logRequest(String message)
    }

    static class TestNativeFileSystemFactory extends NativeFileSystemFactory {

        String rootPath

        List<FileRequestLogger> logger

        public TestNativeFileSystemFactory(String rootPath, FileRequestLogger... logger) {
            this.rootPath = rootPath
            this.logger = Arrays.asList(logger)
        }

        /**
         * Create the appropriate user file system view.
         */
        public FileSystemView createFileSystemView(Session session) {
            String userName = session.getUsername();
            FileSystemView fsView = new TestNativeFileSystemView(rootPath, userName, logger, caseInsensitive);
            return fsView;
        }
    }

    static class TestNativeFileSystemView implements FileSystemView {
        // the first and the last character will always be '/'
        // It is always with respect to the root directory.
        private String currDir;

        private String userName;

        private boolean caseInsensitive = false;

        List<FileRequestLogger> logger

        /**
         * Constructor - internal do not use directly, use {@link NativeFileSystemFactory} instead
         */
        public TestNativeFileSystemView(String rootpath, String userName, List<FileRequestLogger> requestLoggerList, boolean caseInsensitive) {
            if (!rootpath) {
                throw new IllegalArgumentException("rootPath must be set");
            }

            if (!userName) {
                throw new IllegalArgumentException("user can not be null");
            }

            this.logger = requestLoggerList;
            this.caseInsensitive = caseInsensitive;

            currDir = rootpath;
            this.userName = userName;
        }

        /**
         * Get file object.
         */
        public SshFile getFile(String file) {
            return getFile(currDir, file);
        }

        public SshFile getFile(SshFile baseDir, String file) {
            return getFile(baseDir.getAbsolutePath(), file);
        }

        protected SshFile getFile(String dir, String file) {
            // get actual file object

            String physicalName = NativeSshFile.getPhysicalName("/", dir, file, caseInsensitive);
            File fileObj = new File(physicalName);
            logFileRequest(dir, fileObj.absolutePath);
            // strip the root directory and return
            String userFileName = physicalName.substring("/".length() - 1);
            return new NativeSshFile(userFileName, fileObj, userName);
        }

        void logFileRequest(String dir, String file) {
            //log xml and jar requests only
            if (file.endsWith("xml") || file.endsWith(".jar")) {
                String normalizedPath = (file - dir).replaceAll("\\\\", '/') - "/"
                logger.each {
                    it.logRequest(normalizedPath)
                }
            }
        }
    }



}


