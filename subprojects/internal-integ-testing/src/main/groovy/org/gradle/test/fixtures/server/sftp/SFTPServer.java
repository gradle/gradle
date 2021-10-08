package org.gradle.test.fixtures.server.sftp;

import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.common.subsystem.sftp.SftpConstants;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.sftp.SftpErrorStatusDataHandler;
import org.apache.sshd.server.subsystem.sftp.SftpFileSystemAccessor;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystem;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.apache.sshd.server.subsystem.sftp.UnsupportedAttributePolicy;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.ivy.RemoteIvyRepository;
import org.gradle.test.fixtures.server.ExpectOne;
import org.gradle.test.fixtures.server.RepositoryServer;
import org.gradle.test.fixtures.server.ServerExpectation;
import org.gradle.test.fixtures.server.ServerWithExpectations;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SFTPServer extends ServerWithExpectations implements RepositoryServer {
    public SFTPServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
        this.hostAddress = "127.0.0.1";
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void resetExpectations() {
        try {
            super.resetExpectations();
        } finally {
            handleCreatedByRequest.clear();
            openingRequestIdForPath.clear();
            allowInit();
        }
    }

    /**
     * this basically restarts the sftpserver without
     * registering a password authentication
     */
    public void withPasswordAuthenticationDisabled() throws IOException {
        passwordAuthenticationEnabled = false;
        restart();
    }

    @Override
    protected void before() throws IOException {
        baseDir = testDirectoryProvider.getTestDirectory().createDir("sshd/files");
        configDir = testDirectoryProvider.getTestDirectory().createDir("sshd/config");

        sshd = setupConfiguredTestSshd();
        sshd.start();
        port = sshd.getPort();
        allowInit();
    }

    public void stop(boolean immediately) throws IOException {
        sshd.stop(immediately);
    }

    @Override
    public void stop() {
        try {
            stop(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void restart() throws IOException {
        stop(true);
        before();
    }

    public void clearSessions() {
        for (AbstractSession activeSession : sshd.getActiveSessions()) {
            activeSession.close(true);
        }
    }

    @Override
    protected void after() {
        super.after();
        passwordAuthenticationEnabled = true;
    }

    private SshServer setupConfiguredTestSshd() throws IOException {
        //copy dsa key to config directory
        URL fileUrl = ClassLoader.getSystemResource("sshd-config/test-dsa.key");
        FileUtils.copyURLToFile(fileUrl, new File(configDir, "test-dsa.key"));

        SshServer sshServer = SshServer.setUpDefaultServer();
        // Set the port to 0 to have it automatically assign a port
        sshServer.setPort(0);
        sshServer.setFileSystemFactory(new TestVirtualFileSystemFactory());
        sshServer.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory() {
            public Command create() {
                return new TestSftpSubsystem();
            }

        }));
        sshServer.setCommandFactory(new ScpCommandFactory());
        sshServer.setKeyPairProvider(new GeneratingKeyPairProvider());

        if (passwordAuthenticationEnabled) {
            sshServer.setPasswordAuthenticator(new DummyPasswordAuthenticator());
        }


        sshServer.setPublickeyAuthenticator((username, key, session) -> true);
        return sshServer;
    }

    public TestFile file(String expectedPath) {
        return new TestFile(new File(baseDir, expectedPath));
    }

    public URI getUri() throws URISyntaxException {
        return new URI("sftp://" + getHostAddress() + ":" + getPort());
    }

    public void allowAll() {
        DefaultGroovyMethods.leftShift(expectations, new SftpAllowAll());
    }

    public void allowInit() {
        DefaultGroovyMethods.leftShift(expectations, new SftpAllow(SftpConstants.SSH_FXP_INIT));
    }

    public void expectLstat(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path));
    }

    public void expectMetadataRetrieve(String path) {
        expectLstat(path);
    }

    public void expectOpen(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOneOpen(SftpConstants.SSH_FXP_OPEN, "OPEN", path));
    }

    public void allowRead(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpAllowHandle(SftpConstants.SSH_FXP_READ, path));
    }

    public void expectClose(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOneHandle(SftpConstants.SSH_FXP_CLOSE, "CLOSE", path));
    }

    public void expectFileDownload(String path) {
        expectOpen(path);
        allowRead(path);
        expectClose(path);
    }

    public void expectFileUpload(String path) {
        expectOpen(path);
        allowWrite(path);
        expectClose(path);
    }

    public void expectStat(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_STAT, "STAT", path));
    }

    public void expectMkdir(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_MKDIR, "MKDIR", path));
    }

    public void expectOpendir(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOneOpen(SftpConstants.SSH_FXP_OPENDIR, "OPENDIR", path));
    }

    public void allowReaddir(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpAllowHandle(SftpConstants.SSH_FXP_READDIR, path));
    }

    public void allowWrite(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpAllowHandle(SftpConstants.SSH_FXP_WRITE, path));
    }

    public void expectDirectoryList(String path) {
        expectOpendir(path);
        allowReaddir(path);
        expectClose(path);
    }

    public void expectLstatBroken(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path, true));
    }

    public void expectMkdirBroken(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_MKDIR, "MKDIR", path, true));
    }

    public void expectMetadataRetrieveBroken(String path) {
        expectLstatBroken(path);
    }

    public void expectWriteBroken(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOneHandle(SftpConstants.SSH_FXP_WRITE, "WRITE", path, true));
    }

    public void expectLstatMissing(String path) {
        DefaultGroovyMethods.leftShift(expectations, new SftpExpectOnePath(SftpConstants.SSH_FXP_LSTAT, "LSTAT", path, false, true));
    }

    public RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern, String ivyFilePattern, String artifactFilePattern) {
        return new IvySftpRepository(this, "/repo", m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern);
    }

    public RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern) {
        return getRemoteIvyRepo(m2Compatible, dirPattern, null, null);
    }

    public RemoteIvyRepository getRemoteIvyRepo() {
        return getRemoteIvyRepo(false, null, null, null);
    }

    public RemoteIvyRepository getRemoteIvyRepo(String contextPath) {
        return new IvySftpRepository(this, contextPath, false, null);
    }

    public String getValidCredentials() {
        return "\n            credentials {\n                username 'sftp'\n                password 'sftp'\n            }\n        ";
    }

    public final String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public TestFile getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(TestFile baseDir) {
        this.baseDir = baseDir;
    }

    public Map<Integer, String> getHandleCreatedByRequest() {
        return handleCreatedByRequest;
    }

    public Map<String, Integer> getOpeningRequestIdForPath() {
        return openingRequestIdForPath;
    }

    public List<SftpExpectation> getExpectations() {
        return expectations;
    }

    public void setExpectations(List<SftpExpectation> expectations) {
        this.expectations = expectations;
    }

    private static final Logger logger = LoggerFactory.getLogger(SFTPServer.class);
    private final String hostAddress;
    private int port;
    private final TestDirectoryProvider testDirectoryProvider;
    private TestFile configDir;
    private SshServer sshd;
    private TestFile baseDir;
    private final Map<Integer, String> handleCreatedByRequest = new LinkedHashMap<>();
    private final Map<String, Integer> openingRequestIdForPath = new LinkedHashMap<>();
    private List<SftpExpectation> expectations = new ArrayList<>();
    private boolean passwordAuthenticationEnabled = true;

    public static class DummyPasswordAuthenticator implements PasswordAuthenticator {
        public boolean authenticate(String username, String password, ServerSession session) {
            return GUtil.isTrue(username) && GUtil.isTrue(password) && username.equals(password);
        }
    }

    public class TestVirtualFileSystemFactory extends VirtualFileSystemFactory {
        public TestVirtualFileSystemFactory() {
            setDefaultHomeDir(getBaseDir().toPath());
        }
    }

    public class TestSftpSubsystem extends SftpSubsystem {
        public TestSftpSubsystem() {
            super(null, true, UnsupportedAttributePolicy.ThrowException, SftpFileSystemAccessor.DEFAULT, SftpErrorStatusDataHandler.DEFAULT);
        }

        @Override
        protected void doProcess(final Buffer buffer, int length, final int type, final int id) throws IOException {
            int originalBufferPosition = buffer.rpos();

            int pos = buffer.rpos();
            String command = commandMessage(buffer, type);
            DefaultGroovyMethods.println(this, "Handling " + command);
            buffer.rpos(pos);

            SftpExpectation matched = getExpectations().stream()
                .filter(expectation -> expectation.matches(buffer, type, id))
                .findFirst()
                .orElse(null);
            if (DefaultGroovyMethods.asBoolean(matched)) {
                if (matched.isFailing()) {
                    sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_FAILURE, "Failure");
                    buffer.rpos(originalBufferPosition + length);
                } else if (matched.isMissing()) {
                    sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_NO_SUCH_FILE, "No such file");
                    buffer.rpos(originalBufferPosition + length);
                } else {
                    buffer.rpos(originalBufferPosition);
                    super.doProcess(buffer, length, type, id);
                }
            } else {
                onFailure(new AssertionError("Unexpected SFTP command: " + command));
                sendStatus(prepareReply(new ByteArrayBuffer()), id, SftpConstants.SSH_FX_FAILURE, "Unexpected command");
                buffer.rpos(originalBufferPosition + length);
            }
        }

        @Override
        protected void sendHandle(Buffer buffer, int id, String handle) throws IOException {
            super.sendHandle(buffer, id, handle);
            SFTPServer.this.getHandleCreatedByRequest().put(id, handle);
        }

        private String commandMessage(final Buffer buffer, int type) {
            switch (type) {
                case 1:
                    return "INIT";
                case 7:
                    return "LSTAT for " + buffer.getString();
                case 3:
                    return "OPEN for " + buffer.getString();
                case 5:
                    return "READ";
                case 4:
                    return "CLOSE";
                case 16:
                    return "REALPATH for " + buffer.getString();
                case 17:
                    return "STAT for " + buffer.getString();
                case 11:
                    return "OPENDIR for " + buffer.getString();
                case 12:
                    return "READDIR for " + buffer.getString();
                case 14:
                    return "MKDIR for " + buffer.getString();
                case 6:
                    return "WRITE";
            }
            return String.valueOf(type);
        }

    }

    public interface SftpExpectation extends ServerExpectation {
        boolean matches(Buffer buffer, int type, int id);

        boolean isFailing();

        boolean isMissing();
    }

    public static class SftpExpectOne extends ExpectOne implements SftpExpectation {
        public SftpExpectOne(int type, String notMetMessage, boolean failing, boolean missing) {
            this.expectedType = type;
            this.notMetMessage = "Expected SFTP command not received: " + notMetMessage;
            this.failing = failing;
            this.missing = missing;
        }

        public boolean matches(Buffer buffer, int type, int id) {
            if (!isRun() && type == expectedType) {
                int originalBufferPosition = buffer.rpos();
                getAtomicRun().set(bufferMatches(buffer, id));
                buffer.rpos(originalBufferPosition);
                return isRun();
            } else {
                return false;
            }

        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            return true;
        }

        public final String getNotMetMessage() {
            return notMetMessage;
        }

        public final boolean getFailing() {
            return failing;
        }

        public final boolean isFailing() {
            return failing;
        }

        public final boolean getMissing() {
            return missing;
        }

        public final boolean isMissing() {
            return missing;
        }

        private final int expectedType;
        private final String notMetMessage;
        private final boolean failing;
        private final boolean missing;
    }

    public static class SftpExpectOnePath extends SftpExpectOne {
        public SftpExpectOnePath(int type, String commandName, String path, boolean failing, boolean missing) {
            super(type, commandName + " for " + path, failing, missing);
            this.path = path;
        }

        public SftpExpectOnePath(int type, String commandName, String path, boolean failing) {
            this(type, commandName, path, failing, false);
        }

        public SftpExpectOnePath(int type, String commandName, String path) {
            this(type, commandName, path, false, false);
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            return buffer.getString().equals(path);
        }

        public final String getPath() {
            return path;
        }

        private final String path;
    }

    public class SftpExpectOneOpen extends SftpExpectOnePath {
        public SftpExpectOneOpen(int type, String commandName, String path, boolean failing) {
            super(type, commandName, path, failing);
        }

        public SftpExpectOneOpen(int type, String commandName, String path) {
            this(type, commandName, path, false);
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            boolean matched = buffer.getString().equals(getPath());
            if (matched) {
                SFTPServer.this.getOpeningRequestIdForPath().put(getPath(), id);
            }

            return matched;
        }

    }

    public class SftpExpectOneHandle extends SftpExpectOnePath {
        public SftpExpectOneHandle(int type, String commandName, String path, boolean failing) {
            super(type, commandName, path, failing);
        }

        public SftpExpectOneHandle(int type, String commandName, String path) {
            this(type, commandName, path, false);
        }

        protected boolean bufferMatches(Buffer buffer, int id) {
            String handle = buffer.getString();
            Integer openingRequestId = SFTPServer.this.getOpeningRequestIdForPath().get(getPath());
            return GUtil.isTrue(openingRequestId) && handle.equals(SFTPServer.this.getHandleCreatedByRequest().get(openingRequestId));
        }

    }

    public static class SftpAllow implements SftpExpectation {
        public SftpAllow(int expectedType) {
            this.expectedType = expectedType;
        }

        public boolean matches(Buffer buffer, int type, int id) {
            return type == expectedType;
        }

        public void assertMet() {
            //can never be not met
        }

        public final boolean getFailing() {
            return failing;
        }

        public final boolean isFailing() {
            return failing;
        }

        public final boolean getMissing() {
            return missing;
        }

        public final boolean isMissing() {
            return missing;
        }

        private final boolean failing = false;
        private final boolean missing = false;
        private final int expectedType;
    }

    public static class SftpAllowAll implements SftpExpectation {
        public boolean matches(Buffer buffer, int type, int id) {
            return true;
        }

        public void assertMet() {
            //can never be not met
        }

        public final boolean getFailing() {
            return failing;
        }

        public final boolean isFailing() {
            return failing;
        }

        public final boolean getMissing() {
            return missing;
        }

        public final boolean isMissing() {
            return missing;
        }

        private final boolean failing = false;
        private final boolean missing = false;
    }

    public class SftpAllowHandle implements SftpExpectation {
        public SftpAllowHandle(int type, String path) {
            this.expectedType = type;
            this.path = path;
        }

        public void assertMet() {
            //can never be not met
        }

        public boolean matches(Buffer buffer, int type, int id) {
            if (type == expectedType) {
                int originalBufferPosition = buffer.rpos();
                String handle = buffer.getString();
                Integer openingRequestId = SFTPServer.this.getOpeningRequestIdForPath().get(path);
                boolean matched = GUtil.isTrue(openingRequestId) && handle.equals(SFTPServer.this.getHandleCreatedByRequest().get(openingRequestId));
                buffer.rpos(originalBufferPosition);
                return matched;
            } else {
                return false;
            }
        }

        public final boolean getFailing() {
            return failing;
        }

        public final boolean isFailing() {
            return failing;
        }

        public final boolean getMissing() {
            return missing;
        }

        public final boolean isMissing() {
            return missing;
        }

        public final String getPath() {
            return path;
        }

        private final int expectedType;
        private final boolean failing = false;
        private final boolean missing = false;
        private final String path;
    }

    public static class GeneratingKeyPairProvider extends AbstractKeyPairProvider {

        private final KeyPair keyPair;

        public GeneratingKeyPairProvider() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(1024, SecureRandom.getInstance("SHA1PRNG"));
                keyPair = generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Iterable<KeyPair> loadKeys() {
            return Collections.singletonList(keyPair);
        }
    }
}
