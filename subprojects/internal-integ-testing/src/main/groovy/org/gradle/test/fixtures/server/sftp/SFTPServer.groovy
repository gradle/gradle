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

import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.scp.server.ScpCommandFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.sftp.common.SftpConstants
import org.apache.sshd.sftp.server.SftpErrorStatusDataHandler
import org.apache.sshd.sftp.server.SftpEventListener
import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.sftp.server.UnsupportedAttributePolicy
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.ExpectOne
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.ServerExpectation
import org.gradle.test.fixtures.server.ServerWithExpectations
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom

@CompileStatic
class SFTPServer extends ServerWithExpectations implements RepositoryServer {

    private final static Logger logger = LoggerFactory.getLogger(SFTPServer)
    final String hostAddress
    int port

    private final TestDirectoryProvider testDirectoryProvider
    private TestFile configDir
    private SshServer sshd

    TestFile baseDir
    Map<Integer, String> handleCreatedByRequest = [:]
    Map<String, Integer> openingRequestIdForPath = [:]
    List<SftpExpectation> expectations = []
    private boolean passwordAuthenticationEnabled = true

    SFTPServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
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
            allowInit()
        }
    }

    /**
     * this basically restarts the sftpserver without
     * registering a password authentication
     * */
    def withPasswordAuthenticationDisabled() {
        passwordAuthenticationEnabled = false
        restart()
    }

    protected void before() throws Throwable {
        baseDir = testDirectoryProvider.getTestDirectory().createDir("sshd/files")
        configDir = testDirectoryProvider.getTestDirectory().createDir("sshd/config")

        // Set the port to 0 to have it automatically assign a port
        sshd = setupConfiguredTestSshd(0)
        sshd.start()
        port = sshd.getPort()
        allowInit()
    }

    void stop(boolean immediately = true) {
        sshd?.stop(immediately)
    }

    void restart() {
        stop(true)
        before()
    }

    void clearSessions() {
        sshd.activeSessions.each { session ->
            session.close(true)
        }
    }

    @Override
    protected void after() {
        super.after()
        passwordAuthenticationEnabled = true
    }

    private SshServer setupConfiguredTestSshd(int sshPort) {
        //copy dsa key to config directory
        URL fileUrl = ClassLoader.getSystemResource("sshd-config/test-dsa.key")
        FileUtils.copyURLToFile(fileUrl, new File(configDir, "test-dsa.key"))

        SftpSubsystemFactory sftpSubsystemFactory = new SftpSubsystemFactory.Builder()
            .withUnsupportedAttributePolicy(UnsupportedAttributePolicy.ThrowException)
            .withFileSystemAccessor(SftpFileSystemAccessor.DEFAULT)
            .withSftpErrorStatusDataHandler(SftpErrorStatusDataHandler.DEFAULT)
            .build()
        sftpSubsystemFactory.addSftpEventListener(new TestSftpEventListener())

        SshServer sshServer = SshServer.setUpDefaultServer()
        sshServer.setPort(sshPort)
        sshServer.setFileSystemFactory(new TestVirtualFileSystemFactory())
        sshServer.setSubsystemFactories([
            sftpSubsystemFactory
        ])
        sshServer.setCommandFactory(new ScpCommandFactory())
        sshServer.setKeyPairProvider(new GeneratingKeyPairProvider())

        if (passwordAuthenticationEnabled) {
            sshServer.setPasswordAuthenticator(new DummyPasswordAuthenticator())
        }

        sshServer.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            boolean authenticate(String username, PublicKey key, ServerSession session) {
                return true
            }
        })
        return sshServer
    }


    boolean hasFile(String filePathToCheck) {
        new File(baseDir, filePathToCheck).exists()
    }

    TestFile file(String expectedPath) {
        new TestFile(new File(baseDir, expectedPath))
    }

    URI getUri() {
        return new URI("sftp://${hostAddress}:${port}")
    }

    void allowAll() {
        expectations << new SftpAllowAll()
    }

    void allowInit() {
        expectations << new SftpAllow(SftpConstants.SSH_FXP_INIT)
    }

    void expectLstat(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path)
    }

    void expectMetadataRetrieve(String path) {
        expectLstat(path)
    }

    void expectOpen(String path) {
        expectations << new SftpExpectOneOpen(SftpConstants.SSH_FXP_OPEN, "OPEN", path)
    }

    void allowRead(String path) {
        expectations << new SftpAllowHandle(SftpConstants.SSH_FXP_READ, path)
    }

    void expectClose(String path) {
        expectations << new SftpExpectOneHandle(SftpConstants.SSH_FXP_CLOSE, "CLOSE", path)
    }

    void expectFileDownload(String path) {
        expectOpen(path)
        allowRead(path)
        expectClose(path)
    }

    void expectFileUpload(String path) {
        expectOpen(path)
        allowWrite(path)
        expectClose(path)
    }

    void expectRealpath(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_REALPATH, "REALPATH", path)
    }

    void expectStat(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_STAT, "STAT", path)
    }

    void expectMkdir(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_MKDIR, "MKDIR", path)
    }

    void expectOpendir(String path) {
        expectations << new SftpExpectOneOpen(SftpConstants.SSH_FXP_OPENDIR, "OPENDIR", path)
    }

    void allowReaddir(String path) {
        expectations << new SftpAllowHandle(SftpConstants.SSH_FXP_READDIR, path)
    }

    void allowWrite(String path) {
        expectations << new SftpAllowHandle(SftpConstants.SSH_FXP_WRITE, path)
    }

    void expectDirectoryList(String path) {
        expectOpendir(path)
        allowReaddir(path)
        expectClose(path)
    }

    void expectLstatBroken(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path, true)
    }

    void expectMkdirBroken(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_MKDIR, "MKDIR", path, true)
    }

    void expectMetadataRetrieveBroken(String path) {
        expectLstatBroken(path)
    }

    void expectWriteBroken(String path) {
        expectations << new SftpExpectOneHandle(SftpConstants.SSH_FXP_WRITE, "WRITE", path, true)
    }

    void expectLstatMissing(String path) {
        expectations << new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path, false, true)
    }

    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        new IvySftpRepository(this, '/repo', m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    RemoteIvyRepository getRemoteIvyRepo(String contextPath) {
        new IvySftpRepository(this, contextPath, false, null)
    }

    String getValidCredentials() {
        return """
            credentials {
                username 'sftp'
                password 'sftp'
            }
        """
    }

    static class DummyPasswordAuthenticator implements PasswordAuthenticator {
        // every combination where username == password is accepted
        boolean authenticate(String username, String password, ServerSession session) {
            return username && password && username == password
        }
    }

    class TestVirtualFileSystemFactory extends VirtualFileSystemFactory {
        TestVirtualFileSystemFactory() {
            setDefaultHomeDir(baseDir.toPath())
        }
    }

    class TestSftpEventListener implements SftpEventListener {

        @Override
        void received(ServerSession session, int type, int id) throws IOException {
            println "received $type $id"
        }

//        TestSftpSubsystem() {
//
//            // FIXME: Probably broken
//            super(null, true, UnsupportedAttributePolicy.ThrowException, SftpFileSystemAccessor.DEFAULT, SftpErrorStatusDataHandler.DEFAULT)
//        }
//
//        @Override
//        protected void doProcess(Buffer buffer, int length, int type, int id) throws IOException {
//            int originalBufferPosition = buffer.rpos()
//
//            int pos = buffer.rpos()
//            def command = commandMessage(buffer, type)
//            println("Handling $command")
//            buffer.rpos(pos)
//
//            def matched = expectations.find { it.matches(buffer, type, id) }
//            if (matched) {
//                if (matched.failing) {
//                    sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_FAILURE, "Failure")
//                    buffer.rpos(originalBufferPosition + length)
//                } else if (matched.missing) {
//                    sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_NO_SUCH_FILE, "No such file")
//                    buffer.rpos(originalBufferPosition + length)
//                } else {
//                    buffer.rpos(originalBufferPosition)
//                    super.doProcess(buffer, length, type, id)
//                }
//            } else {
//                onFailure(new SftpAssertionError("Unexpected SFTP command: $command"))
//                sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_FAILURE, "Unexpected command")
//                buffer.rpos(originalBufferPosition + length)
//            }
//        }
//
//        @Override
//        protected void sendHandle(Buffer buffer, int id, String handle) throws IOException {
//            super.sendHandle(buffer, id, handle)
//            SFTPServer.this.handleCreatedByRequest[id] = handle
//        }
//
//        private String commandMessage(Buffer buffer, int type) {
//            switch (type) {
//                case SftpConstants.SSH_FXP_INIT:
//                    return "INIT"
//                case SftpConstants.SSH_FXP_LSTAT:
//                    return "LSTAT for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_OPEN:
//                    return "OPEN for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_READ:
//                    return "READ"
//                case SftpConstants.SSH_FXP_CLOSE:
//                    return "CLOSE"
//                case SftpConstants.SSH_FXP_REALPATH:
//                    return "REALPATH for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_STAT:
//                    return "STAT for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_OPENDIR:
//                    return "OPENDIR for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_READDIR:
//                    return "READDIR for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_MKDIR:
//                    return "MKDIR for ${buffer.getString()}"
//                case SftpConstants.SSH_FXP_WRITE:
//                    return "WRITE"
//            }
//            return type
//        }
    }

    static interface SftpExpectation extends ServerExpectation {
        boolean matches(Buffer buffer, int type, int id)

        boolean isFailing()

        boolean isMissing()
    }

    static class SftpExpectOne extends ExpectOne implements SftpExpectation {

        final int expectedType
        final String notMetMessage
        final Closure matcher
        final boolean failing
        final boolean missing

        SftpExpectOne(int type, String notMetMessage, boolean failing = false, boolean missing = false) {
            this.expectedType = type
            this.notMetMessage = "Expected SFTP command not received: $notMetMessage"
            this.matcher = matcher
            this.failing = failing
            this.missing = missing
        }

        boolean matches(Buffer buffer, int type, int id) {
            if (!run && type == expectedType) {
                int originalBufferPosition = buffer.rpos()
                atomicRun.set(bufferMatches(buffer, id))
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

        final String path

        SftpExpectOnePath(int type, String commandName, String path, boolean failing = false, boolean missing = false) {
            super(type, "$commandName for $path", failing, missing)
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
                SFTPServer.this.openingRequestIdForPath[path] = id
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
            def openingRequestId = SFTPServer.this.openingRequestIdForPath[path]
            return openingRequestId && handle == SFTPServer.this.handleCreatedByRequest[openingRequestId]
        }
    }

    class SftpAllow implements SftpExpectation {

        final boolean failing = false
        final boolean missing = false
        final int expectedType

        SftpAllow(int expectedType) {
            this.expectedType = expectedType
        }

        boolean matches(Buffer buffer, int type, int id) {
            return type == expectedType
        }

        void assertMet() {
            //can never be not met
        }
    }

    class SftpAllowAll implements SftpExpectation {

        final boolean failing = false
        final boolean missing = false

        boolean matches(Buffer buffer, int type, int id) {
            return true
        }

        void assertMet() {
            //can never be not met
        }
    }

    class SftpAllowHandle implements SftpExpectation {

        final int expectedType
        final boolean failing = false
        final boolean missing = false
        final String path

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
                def openingRequestId = SFTPServer.this.openingRequestIdForPath[path]
                def matched = openingRequestId && handle == SFTPServer.this.handleCreatedByRequest[openingRequestId]
                buffer.rpos(originalBufferPosition)
                return matched
            } else {
                return false
            }
        }
    }

    class GeneratingKeyPairProvider extends AbstractKeyPairProvider {

        KeyPair keyPair

        GeneratingKeyPairProvider() {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(1024, SecureRandom.getInstance("SHA1PRNG"))
            keyPair = generator.generateKeyPair()
        }

        @Override
        Iterable<KeyPair> loadKeys(SessionContext session) throws IOException, GeneralSecurityException {
            return [keyPair]
        }
    }

    class SftpAssertionError extends AssertionError {
        protected SftpAssertionError(String message) {
            this(message)
        }
    }
}


