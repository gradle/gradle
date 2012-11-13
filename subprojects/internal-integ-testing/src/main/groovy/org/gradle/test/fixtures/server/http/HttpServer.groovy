/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.test.fixtures.server.http

import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.hash.HashUtil
import org.hamcrest.Matcher
import org.junit.rules.ExternalResource
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.Principal
import java.util.zip.GZIPOutputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.mortbay.jetty.*
import org.mortbay.jetty.security.*

class HttpServer extends ExternalResource {

    private static Logger logger = LoggerFactory.getLogger(HttpServer.class)

    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()
    private TestUserRealm realm
    private SecurityHandler securityHandler
    private Connector connector
    private SslSocketConnector sslConnector
    AuthScheme authenticationScheme = AuthScheme.BASIC

    private Throwable failure
    private final List<Expection> expections = []
    private Matcher expectedUserAgent = null

    enum AuthScheme {
        BASIC(new BasicAuthHandler()), DIGEST(new DigestAuthHandler())

        final AuthSchemeHandler handler;

        AuthScheme(AuthSchemeHandler handler) {
            this.handler = handler
        }
    }

    enum EtagStrategy {
        NONE({ null }),
        RAW_SHA1_HEX({ HashUtil.sha1(it as byte[]).asHexString() }),
        NEXUS_ENCODED_SHA1({ "{SHA1{" + HashUtil.sha1(it as byte[]).asHexString() + "}}" })

        private final Closure generator

        EtagStrategy(Closure generator) {
            this.generator = generator
        }

        String generate(byte[] bytes) {
            generator.call(bytes)
        }
    }

    // Can be an EtagStrategy, or a closure that receives a byte[] and returns an etag string, or anything that gets toString'd
    def etags = EtagStrategy.NONE

    boolean sendLastModified = true
    boolean sendSha1Header = false

    HttpServer() {
        HandlerCollection handlers = new HandlerCollection()
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                println("handling http request: $request.method $target")
            }
        })
        handlers.addHandler(collection)
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.handled) {
                    return
                }
                onFailure(new AssertionError("Received unexpected ${request.method} request to ${target}."))
                response.sendError(404, "'$target' does not exist")
            }
        })
        server.setHandler(handlers)
    }

    void start() {
        start(0)
    }

    void start(int port) {
        connector = new SocketConnector()
        connector.port = port
        server.addConnector(connector)
        try {
            server.start()
        } catch (java.net.BindException e) {
            //without this, it is not possible to retry starting the server on the same port
            //retrying is useful if we need to start server on a specific port
            //and the OS forces us to wait until it is available.
            server.removeConnector(connector)
            throw e
        }
    }

    void stop() {
        resetExpectations()
        server?.stop()
        if (connector) {
            server?.removeConnector(connector)
        }
    }

    private void onFailure(Throwable failure) {
        logger.error(failure.message)
        if (this.failure == null) {
            this.failure = failure
        }
    }

    void enableSsl(String keyStore, String keyPassword, String trustStore = null, String trustPassword = null) {
        sslConnector = new SslSocketConnector()
        sslConnector.keystore = keyStore
        sslConnector.keyPassword = keyPassword
        if (trustStore) {
            sslConnector.needClientAuth = true
            sslConnector.truststore = trustStore
            sslConnector.trustPassword = trustPassword
        }
        server.addConnector(sslConnector)
    }

    int getSslPort() {
        sslConnector.localPort
    }

    void expectUserAgent(UserAgentMatcher userAgent) {
        this.expectedUserAgent = userAgent;
    }

    void resetExpectations() {
        try {
            if (failure != null) {
                throw failure
            }
            for (Expection e in expections) {
                e.assertMet()
            }
        } finally {
            failure = null
            expectedUserAgent = null
            expections.clear()
            collection.setHandlers()
        }
    }

    @Override
    protected void after() {
        stop()
    }

    /**
     * Adds a given file at the given URL. The source file can be either a file or a directory.
     */
    void allowGetOrHead(String path, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], fileHandler(path, srcFile))
    }

    /**
     * Adds a given file at the given URL. The source file can be either a file or a directory.
     */
    void allowHead(String path, File srcFile) {
        allow(path, true, ['HEAD'], fileHandler(path, srcFile))
    }

    /**
     * Adds a given file at the given URL with the given credentials. The source file can be either a file or a directory.
     */
    void allowGetOrHead(String path, String username, String password, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    private Action fileHandler(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        return new Action() {
            String getDisplayName() {
                return "return contents of $srcFile.name"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (HttpServer.this.expectedUserAgent != null) {
                    String receivedUserAgent = request.getHeader("User-Agent")
                    if (!expectedUserAgent.matches(receivedUserAgent)) {
                        response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent));
                        return;
                    }
                }
                def file
                if (request.pathInfo == path) {
                    file = srcFile
                } else {
                    def relativePath = request.pathInfo.substring(path.length() + 1)
                    file = new File(srcFile, relativePath)
                }
                if (file.isFile()) {
                    sendFile(response, file, lastModified, contentLength)
                } else if (file.isDirectory()) {
                    sendDirectoryListing(response, file)
                } else {
                    response.sendError(404, "'$request.pathInfo' does not exist")
                }
            }
        }
    }

    /**
     * Adds a broken resource at the given URL.
     */
    void addBroken(String path) {
        allow(path, true, null, new Action() {
            String getDisplayName() {
                return "return 500 broken"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(500, "broken")
            }
        })
    }

    /**
     * Allows one GET request for the given URL, which return 404 status code
     */
    void expectGetMissing(String path) {
        expect(path, false, ['GET'], notFound())
    }

    /**
     * Allows one HEAD request for the given URL, which return 404 status code
     */
    void expectHeadMissing(String path) {
        expect(path, false, ['HEAD'], notFound())
    }

    private Action notFound() {
        new Action() {
            String getDisplayName() {
                return "return 404 not found"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(404, "not found")
            }
        }
    }

    /**
     * Allows one HEAD request for the given URL.
     */
    void expectHead(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        expect(path, false, ['HEAD'], fileHandler(path, srcFile, lastModified, contentLength))
    }

    /**
     * Allows one HEAD request for the given URL with http authentication.
     */
    void expectHead(String path, String username, String password, File srcFile, Long lastModified = null, Long contentLength = null) {
        expect(path, false, ['HEAD'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Allows one GET request for the given URL. Reads the request content from the given file.
     */
    void expectGet(String path, File srcFile, Long lastModified = null, Long contentLength = null) {
        expect(path, false, ['GET'], fileHandler(path, srcFile, lastModified, contentLength))
    }

    /**
     * Allows one GET request for the given URL, with the given credentials. Reads the request content from the given file.
     */
    void expectGet(String path, String username, String password, File srcFile) {
        expect(path, false, ['GET'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Allows one GET request for the given URL, with the response being GZip encoded.
     */
    void expectGetGZipped(String path, File srcFile) {
        expect(path, false, ['GET'], new Action() {
            String getDisplayName() {
                return "return gzipped $srcFile.name"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                def file = srcFile
                if (file.isFile()) {
                    response.setHeader("Content-Encoding", "gzip")
                    response.setDateHeader(HttpHeaders.LAST_MODIFIED, srcFile.lastModified())
                    def stream = new GZIPOutputStream(response.outputStream)
                    stream.write(file.bytes)
                    stream.close()
                } else {
                    response.sendError(404, "'$target' does not exist")
                }
            }
        });
    }

    /**
     * Allow one GET request for the given URL, responding with a redirect.
     */
    void expectGetRedirected(String path, String location) {
        expectRedirected('GET', path, location)
    }

    /**
     * Allow one HEAD request for the given URL, responding with a redirect.
     */
    void expectHeadRedirected(String path, String location) {
        expectRedirected('HEAD', path, location)
    }

    private void expectRedirected(String method, String path, String location) {
        expect(path, false, [method], new Action() {
            String getDisplayName() {
                return "redirect to $location"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendRedirect(location)
            }
        })
    }

    /**
     * Allows one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, File directory) {
        expect(path, false, ['GET'], new Action() {
            String getDisplayName() {
                return "return listing of directory $directory.name"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        })
    }

    /**
     * Allows one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, String username, String password, File directory) {
        expect(path, false, ['GET'], withAuthentication(path, username, password, new Action() {
            String getDisplayName() {
                return "return listing of directory $directory.name"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        }));
    }


    private sendFile(HttpServletResponse response, File file, Long lastModified, Long contentLength) {
        if (sendLastModified) {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified ?: file.lastModified())
        }
        response.setContentLength((contentLength ?: file.length()) as int)
        response.setContentType(new MimeTypes().getMimeByExtension(file.name).toString())
        if (sendSha1Header) {
            response.addHeader("X-Checksum-Sha1", HashUtil.sha1(file).asHexString())
        }
        addEtag(response, file.bytes, etags)
        response.outputStream << new FileInputStream(file)
    }

    private addEtag(HttpServletResponse response, byte[] bytes, etagStrategy) {
        if (etagStrategy != null) {
            String value
            if (etags instanceof EtagStrategy) {
                value = etags.generate(bytes)
            } else if (etagStrategy instanceof Closure) {
                value = etagStrategy.call(bytes)
            } else {
                value = etagStrategy.toString()
            }

            if (value != null) {
                response.addHeader(HttpHeaders.ETAG, value)
            }
        }
    }

    private sendDirectoryListing(HttpServletResponse response, File directory) {
        def directoryListing = ""
        for (String fileName : directory.list()) {
            directoryListing += "<a href=\"$fileName\">$fileName</a>"
        }

        response.setContentLength(directoryListing.length())
        response.setContentType("text/html")
        response.outputStream.bytes = directoryListing.bytes
    }

    /**
     * Allows one PUT request for the given URL. Writes the request content to the given file.
     */
    void expectPut(String path, File destFile, int statusCode = HttpStatus.ORDINAL_200_OK) {
        expect(path, false, ['PUT'], new Action() {
            String getDisplayName() {
                return "write request to $destFile.name and return status $statusCode"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (HttpServer.this.expectedUserAgent != null) {
                    String receivedUserAgent = request.getHeader("User-Agent")
                    if (!expectedUserAgent.matches(receivedUserAgent)) {
                        response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent))
                        return;
                    }
                }
                destFile.bytes = request.inputStream.bytes
                response.setStatus(statusCode)
            }
        })
    }

    /**
     * Allows one PUT request for the given URL, with the given credentials. Writes the request content to the given file.
     */
    void expectPut(String path, String username, String password, File destFile) {
        expect(path, false, ['PUT'], withAuthentication(path, username, password, new Action() {
            String getDisplayName() {
                return "write request to $destFile.name"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {

                if (request.remoteUser != username) {
                    response.sendError(500, "unexpected username '${request.remoteUser}'")
                    return
                }
                destFile.bytes = request.inputStream.bytes
            }
        }))
    }

    /**
     * Allows PUT requests with the given credentials.
     */
    void allowPut(String path, String username, String password) {
        allow(path, false, ['PUT'], withAuthentication(path, username, password, new Action() {
            String getDisplayName() {
                return "return 500"
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(500, "unexpected username '${request.remoteUser}'")
            }
        }))
    }

    private Action withAuthentication(String path, String username, String password, Action action) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
            authenticationScheme.handler.addConstraint(securityHandler, path)
        } else {
            realm = new TestUserRealm()
            realm.username = username
            realm.password = password
            securityHandler = authenticationScheme.handler.createSecurityHandler(path, realm)
            collection.addHandler(securityHandler)
        }

        return new Action() {
            String getDisplayName() {
                return action.displayName
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (request.remoteUser != username) {
                    response.sendError(500, "unexpected username '${request.remoteUser}'")
                    return
                }
                action.handle(request, response)
            }
        }
    }

    private void expect(String path, boolean recursive, Collection<String> methods, Action action) {
        ExpectOne expectation = new ExpectOne(action, methods, path)
        expections << expectation
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (expectation.run) {
                    return
                }
                expectation.run = true
                action.handle(request, response)
                request.handled = true
            }
        })
    }

    private void allow(String path, boolean recursive, Collection<String> methods, Action action) {
        add(path, recursive, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                action.handle(request, response)
                request.handled = true
            }
        })
    }

    private void add(String path, boolean recursive, Collection<String> methods, Handler handler) {
        assert path.startsWith('/')
//        assert path == '/' || !path.endsWith('/')
        def prefix = path == '/' ? '/' : path + '/'
        collection.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (methods != null && !methods.contains(request.method)) {
                    return
                }
                boolean match = request.pathInfo == path || (recursive && request.pathInfo.startsWith(prefix))
                if (match && !request.handled) {
                    handler.handle(target, request, response, dispatch)
                }
            }
        })
    }

    int getPort() {
        return server.connectors[0].localPort
    }

    interface Expection {
        void assertMet()
    }

    static class ExpectOne implements Expection {
        boolean run
        final Action action
        final Collection<String> methods
        final String path

        ExpectOne(Action action, Collection<String> methods, String path) {
            this.action = action
            this.methods = methods
            this.path = path
        }

        void assertMet() {
            if (!run) {
                throw new AssertionError("Expected HTTP request not received: ${methods.size() == 1 ? methods[0] : methods} $path and $action.displayName")
            }
        }
    }

    interface Action {
        String getDisplayName()

        void handle(HttpServletRequest request, HttpServletResponse response)
    }

    abstract static class AuthSchemeHandler {
        public SecurityHandler createSecurityHandler(String path, TestUserRealm realm) {
            def constraintMapping = createConstraintMapping(path)
            def securityHandler = new SecurityHandler()
            securityHandler.userRealm = realm
            securityHandler.constraintMappings = [constraintMapping] as ConstraintMapping[]
            securityHandler.authenticator = authenticator
            return securityHandler
        }

        public void addConstraint(SecurityHandler securityHandler, String path) {
            securityHandler.constraintMappings = (securityHandler.constraintMappings as List) + createConstraintMapping(path)
        }

        private ConstraintMapping createConstraintMapping(String path) {
            def constraint = new Constraint()
            constraint.name = constraintName()
            constraint.authenticate = true
            constraint.roles = ['*'] as String[]
            def constraintMapping = new ConstraintMapping()
            constraintMapping.pathSpec = path
            constraintMapping.constraint = constraint
            return constraintMapping
        }

        protected abstract String constraintName();

        protected abstract Authenticator getAuthenticator();
    }

    public static class BasicAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__BASIC_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new BasicAuthenticator()
        }
    }

    public static class DigestAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__DIGEST_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new DigestAuthenticator()
        }
    }

    static class TestUserRealm implements UserRealm {
        String username
        String password

        Principal authenticate(String username, Object credentials, Request request) {
            Password passwordCred = new Password(password)
            if (username == this.username && passwordCred.check(credentials)) {
                return getPrincipal(username)
            }
            return null
        }

        String getName() {
            return "test"
        }

        Principal getPrincipal(String username) {
            return new Principal() {
                String getName() {
                    return username
                }
            }
        }

        boolean reauthenticate(Principal user) {
            return false
        }

        boolean isUserInRole(Principal user, String role) {
            return false
        }

        void disassociate(Principal user) {
        }

        Principal pushRole(Principal user, String role) {
            return user
        }

        Principal popRole(Principal user) {
            return user
        }

        void logout(Principal user) {
        }

    }
}
