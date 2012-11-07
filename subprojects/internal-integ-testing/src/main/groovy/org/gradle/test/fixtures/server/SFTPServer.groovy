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

package org.gradle.test.fixtures.server

import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import org.apache.commons.io.FileUtils
import org.apache.sshd.SshServer
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.server.Command
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.rules.ExternalResource

import java.security.PublicKey

class SFTPServer extends ExternalResource {
    final String hostAddress;
    int port

    private final TemporaryFolder tmpDir
    private TestFile baseDir
    private TestFile configDir

    private SshServer sshd;
    private com.jcraft.jsch.Session session

    def fileRequests = [] as Set

    public SFTPServer(TemporaryFolder tmpDir) {
        this.tmpDir = tmpDir;
        def portFinder = org.gradle.util.AvailablePortFinder.createPrivate()
        port = portFinder.nextAvailable
        this.hostAddress = "127.0.0.1"
    }

    protected void before() throws Throwable {
        baseDir = tmpDir.createDir("sshd/files")
        configDir = tmpDir.createDir("sshd/config")

        sshd = setupConfiguredTestSshd();
        sshd.start();
        createSshSession();
    }

    protected void after() {
        session?.disconnect();
        sshd?.stop()
    }

    private createSshSession() {
        JSch sch = new JSch();
        session = sch.getSession("sshd", "localhost", port);
        session.setUserInfo(new UserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return "sshd";
            }

            public boolean promptPassword(String message) {
                return true;
            }

            public boolean promptPassphrase(String message) {
                return false;
            }

            public boolean promptYesNo(String message) {
                return true;
            }

            public void showMessage(String message) {
            }
        });
        session.connect()
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
}


