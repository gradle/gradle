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


import org.apache.commons.io.FileUtils;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;


import org.apache.sshd.SshServer
import org.apache.sshd.server.sftp.SftpSubsystem
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.Command
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.common.NamedFactory
import com.jcraft.jsch.JSch

import com.jcraft.jsch.UserInfo
import org.gradle.util.TestFile
import java.security.PublicKey
import org.apache.sshd.server.session.ServerSession

class SFTPServer extends ExternalResource {
    final int port;
    final String hostAddress;

    private TemporaryFolder baseDir = new TemporaryFolder();
    private TemporaryFolder configDir = new TemporaryFolder();

    private SshServer sshd;
    private com.jcraft.jsch.Session session

    def fileRequests = [] as Set

    public SFTPServer(int port, String hostAddress) {
        this.port = port
        this.hostAddress = hostAddress
    }

    protected void before() throws Throwable {
        baseDir.create()
        configDir.create()

        sshd = setupConfiguredTestSshd();
        sshd.start();
        createSshSession();
    }


    public void after() {
        try {
            session?.disconnect();
            sshd?.stop();
        } catch (Throwable e) {
            e.printStackTrace()
        }
        try {
            configDir.delete();
        } catch (Throwable e) {
            e.printStackTrace()
        }

        try {
            baseDir.delete();
        } catch (Throwable e) {
            e.printStackTrace()
        }
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
        FileUtils.copyURLToFile(fileUrl, new File(configDir.getRoot(), "test-dsa.key"));

        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(port);
        sshServer.setFileSystemFactory(new TestNativeFileSystemFactory(baseDir.getRoot().absolutePath, new FileRequestLogger() {
            void logRequest(String message) {
                fileRequests << message;
            }
        }));
        sshServer.setSubsystemFactories(Arrays.<NamedFactory<Command>> asList(new SftpSubsystem.Factory()));
        sshServer.setCommandFactory(new ScpCommandFactory());
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("${configDir.getRoot()}/test-dsa.key"));
        sshServer.setPasswordAuthenticator(new DummyPasswordAuthenticator());
        sshServer.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            boolean authenticate(String username, PublicKey key, ServerSession session) {
                return true
            }
        });
        return sshServer;
    }

    boolean hasFile(String filePathToCheck) {
        new File(baseDir.getRoot(), filePathToCheck).exists()
    }

    TestFile file(String expectedPath) {
        new TestFile(new File(baseDir.getRoot(), expectedPath))
    }

    public Set<String> getFileRequests() {
        return fileRequests
    }

    public void clearRequests() {
        fileRequests.clear();
    }

}

abstract class FileRequestLogger {
    abstract void logRequest(String message);
}

public class DummyPasswordAuthenticator implements PasswordAuthenticator {

    // every combination where username == password is accepted
    boolean authenticate(String username, String password, org.apache.sshd.server.session.ServerSession session) {
        return username && password && username == password;
    }
}


