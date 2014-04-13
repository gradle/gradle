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
import org.apache.commons.io.FilenameUtils
import org.apache.sshd.SshServer
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.Session
import org.apache.sshd.common.file.FileSystemView
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemView
import org.apache.sshd.common.util.Buffer
import org.apache.sshd.server.Command
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.ExpectOne
import org.gradle.test.fixtures.server.ServerExpectation
import org.gradle.test.fixtures.server.ServerWithExpectations
import org.gradle.util.AvailablePortFinder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.PublicKey

class SFTPServer extends ServerWithExpectations {

    private final static Logger logger = LoggerFactory.getLogger(SFTPServer)
    private final String hostAddress
    private int port

    private final TestDirectoryProvider testDirectoryProvider
    TestFile baseDir
    private TestFile configDir
    private SshServer sshd

    Map<Integer, String> handleCreatedByRequest = [:]
    Map<String, Integer> openingRequestIdForPath = [:]

    SftpSubsystem sftpSubsystem = new TestSftpSubsystem()

    List<SftpExpectation> expectations = []

    public SFTPServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
        this.hostAddress = "127.0.0.1"
    }

    protected Logger getLogger() {
        logger
    }

    @Override
    void resetExpectations() {
        try {
            super.resetExpectations()
        } finally {
            handleCreatedByRequest.clear()
            openingRequestIdForPath.clear()
        }
    }

    protected void before() throws Throwable {
        baseDir = testDirectoryProvider.getTestDirectory().createDir("sshd/files")
        configDir = testDirectoryProvider.getTestDirectory().createDir("sshd/config")

        def portFinder = AvailablePortFinder.createPrivate()
        port = portFinder.nextAvailable
        sshd = setupConfiguredTestSshd();
        sshd.start();
    }

    public void stop() {
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

    void expectInit() {
        expectations << new SftpExpectOne(SftpSubsystem.SSH_FXP_INIT, "INIT")
    }

    void expectLstat(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_LSTAT, "LSTAT", path)
    }

    void expectMetadataRetrieve(String path) {
        expectInit()
        expectLstat(path)
    }

    void expectOpen(String path) {
        expectations << new SftpExpectOneOpen(SftpSubsystem.SSH_FXP_OPEN, "OPEN", path)
    }

    void allowRead(String path) {
        expectations << new SftpAllowHandle(SftpSubsystem.SSH_FXP_READ, path)
    }

    void expectClose(String path) {
        expectations << new SftpExpectOneHandle(SftpSubsystem.SSH_FXP_CLOSE, "CLOSE", path)
    }

    void expectFileDownload(String path) {
        expectInit()
        expectOpen(path)
        allowRead(path)
        expectClose(path)
    }

    void expectFileUpload(String path) {
        expectInit()
        expectLstat(FilenameUtils.getFullPathNoEndSeparator(path))
        expectOpen(path)
        allowWrite(path)
        expectClose(path)
    }

    void expectRealpath(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_REALPATH, "REALPATH", path)
    }

    void expectStat(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_STAT, "STAT", path)
    }

    void expectMkdir(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_MKDIR, "MKDIR", path)
    }

    void expectOpendir(String path) {
        expectations << new SftpExpectOneOpen(SftpSubsystem.SSH_FXP_OPENDIR, "OPENDIR", path)
    }

    void allowReaddir(String path) {
        expectations << new SftpAllowHandle(SftpSubsystem.SSH_FXP_READDIR, path)
    }

    void allowWrite(String path) {
        expectations << new SftpAllowHandle(SftpSubsystem.SSH_FXP_WRITE, path)
    }

    void expectDirectoryList(String path) {
        expectInit()
        expectOpendir(path)
        allowReaddir(path)
        expectClose(path)
    }

    void expectLstatFailure(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_LSTAT, "LSTAT", path, true)
    }

    void expectMkdirFailure(String path) {
        expectations << new SftpExpectOnePath(SftpSubsystem.SSH_FXP_MKDIR, "MKDIR", path, true)
    }

    void expectMetadataRetrieveFailure(String path) {
        expectInit()
        expectLstatFailure(path)
    }

    void expectWriteFailure(String path) {
        expectations << new SftpExpectOneHandle(SftpSubsystem.SSH_FXP_WRITE, "WRITE", path, true)
    }

    static class DummyPasswordAuthenticator implements PasswordAuthenticator {
        // every combination where username == password is accepted
        boolean authenticate(String username, String password, ServerSession session) {
            return username && password && username == password;
        }
    }

    class TestVirtualFileSystemFactory extends VirtualFileSystemFactory {
        /**
         * Create the appropriate user file system view.
         */
        public FileSystemView createFileSystemView(Session session) {
            return new VirtualFileSystemView(session.getUsername(), baseDir.absolutePath);
        }
    }

    class TestSftpSubsystem extends SftpSubsystem {

        @Override
        protected void process(Buffer buffer) throws IOException {
            int originalBufferPosition = buffer.rpos()
            int length = buffer.getInt()
            int type = buffer.getByte()
            int id = buffer.getInt()

            def matched = expectations.find { it.matches(buffer, type, id) }
            if (matched) {
                if (matched.failing) {
                    sendStatus(id, SSH_FX_FAILURE, "Failure")
                    buffer.rpos(originalBufferPosition + length)
                } else {
                    buffer.rpos(originalBufferPosition)
                    super.process(buffer)
                }
            } else {
                def message = unexpectedCommandMessage(buffer, type)
                onFailure(new AssertionError("Unexpected SFTP command: $message"))
                sendStatus(id, SSH_FX_FAILURE, "Unexpected command")
                buffer.rpos(originalBufferPosition + length)
            }
        }

        @Override
        protected void sendHandle(int id, String handle) throws IOException {
            super.sendHandle(id, handle)
            handleCreatedByRequest[id] = handle
        }

        private String unexpectedCommandMessage(Buffer buffer, int type) {
            switch (type) {
                case SSH_FXP_INIT:
                    return "INIT"
                case SSH_FXP_LSTAT:
                    return "LSTAT for ${buffer.getString()}"
                case SSH_FXP_OPEN:
                    return "OPEN for ${buffer.getString()}"
                case SSH_FXP_READ:
                    return "READ"
                case SSH_FXP_CLOSE:
                    return "CLOSE"
                case SSH_FXP_REALPATH:
                    return "REALPATH for ${buffer.getString()}"
                case SSH_FXP_STAT:
                    return "STAT for ${buffer.getString()}"
                case SSH_FXP_OPENDIR:
                    return "OPENDIR for ${buffer.getString()}"
                case SSH_FXP_MKDIR:
                    return "MKDIR for ${buffer.getString()}"
                case SSH_FXP_WRITE:
                    return "WRITE"
            }
            return type;
        }
    }

    static interface SftpExpectation extends ServerExpectation {
        boolean matches(Buffer buffer, int type, int id)

        boolean isFailing()
    }

    static class SftpExpectOne extends ExpectOne implements SftpExpectation {

        int expectedType
        String notMetMessage
        Closure matcher
        boolean failing

        SftpExpectOne(int type, String notMetMessage, boolean failing = false) {
            this.expectedType = type
            this.notMetMessage = "Expected SFTP command not recieved: $notMetMessage"
            this.matcher = matcher
            this.failing = failing
        }

        boolean matches(Buffer buffer, int type, int id) {
            if (!run && type == expectedType) {
                int originalBufferPosition = buffer.rpos()
                run = bufferMatches(buffer, id)
                buffer.rpos(originalBufferPosition)
                return run
            } else {
                return false
            }
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            true
        }
    }

    static class SftpExpectOnePath extends SftpExpectOne {

        String path

        SftpExpectOnePath(int type, String commandName, String path, boolean failing = false) {
            super(type, "$commandName for $path", failing)
            this.path = path
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            buffer.getString() == path
        }
    }

    class SftpExpectOneOpen extends SftpExpectOnePath {

        SftpExpectOneOpen(int type, String commandName, String path, boolean failing = false) {
            super(type, commandName, path, failing)
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            def matched = buffer.getString() == path
            if (matched) {
                openingRequestIdForPath[path] = id
            }
            return matched
        }
    }

    class SftpExpectOneHandle extends SftpExpectOnePath {

        SftpExpectOneHandle(int type, String commandName, String path, boolean failing = false) {
            super(type, commandName, path, failing)
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            def handle = buffer.getString()
            def openingRequestId = openingRequestIdForPath[path]
            return openingRequestId && handle == handleCreatedByRequest[openingRequestId]
        }
    }

    class SftpAllowHandle implements SftpExpectation {

        int expectedType
        boolean failing = false
        String path

        SftpAllowHandle(int type, String path) {
            this.expectedType = type
            this.path = path
        }

        void assertMet() {
            //can never be not met
        }

        boolean matches(Buffer buffer, int type, int id) {
            if (type == expectedType) {
                int originalBufferPosition = buffer.rpos()
                def handle = buffer.getString()
                def openingRequestId = openingRequestIdForPath[path]
                def matched = openingRequestId && handle == handleCreatedByRequest[openingRequestId]
                buffer.rpos(originalBufferPosition)
                return matched
            } else {
                return false
            }
        }
    }
}


