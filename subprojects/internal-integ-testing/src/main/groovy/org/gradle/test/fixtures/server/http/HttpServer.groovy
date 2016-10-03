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

import com.google.common.collect.Sets
import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.google.gson.JsonElement
import groovy.xml.MarkupBuilder
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.server.ExpectOne
import org.gradle.test.fixtures.server.ServerExpectation
import org.gradle.test.fixtures.server.ServerWithExpectations
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GFileUtils
import org.hamcrest.Matcher
import org.mortbay.jetty.*
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection
import org.mortbay.jetty.security.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.security.Principal
import java.util.zip.GZIPOutputStream

class HttpServer extends ServerWithExpectations {

    private final static Logger logger = LoggerFactory.getLogger(HttpServer.class)

    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()
    private TestUserRealm realm
    private SecurityHandler securityHandler
    private Connector connector
    private SslSocketConnector sslConnector
    AuthScheme authenticationScheme = AuthScheme.BASIC
    boolean logRequests = true
    final Set<String> authenticationAttempts = Sets.newLinkedHashSet()

    protected Matcher expectedUserAgent = null

    List<ServerExpectation> expectations = []

    enum AuthScheme {
        BASIC(new BasicAuthHandler()),
        DIGEST(new DigestAuthHandler()),
        HIDE_UNAUTHORIZED(new HideUnauthorizedBasicAuthHandler()),
        NTLM(new NtlmAuthHandler())

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
                String authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
                if (authorization!=null) {
                    authenticationAttempts << authorization.split(" ")[0]
                } else {
                    authenticationAttempts << "None"
                }
                if (logRequests) {
                    println("handling http request: $request.method $target")
                }
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

    protected Logger getLogger() {
        logger
    }

    String getAddress() {
        if (!server.started) {
            server.start()
        }
        getUri().toString()
    }

    URI getUri() {
        return sslConnector ? URI.create("https://localhost:${sslConnector.localPort}") : URI.create("http://localhost:${connector.localPort}")
    }

    boolean isRunning() {
        server.running
    }

    void start() {
        connector = new SocketConnector()
        connector.port = 0
        server.addConnector(connector)
        server.start()
        for (int i = 0; i < 5; i++) {
            if (connector.localPort > 0) {
                return;
            }
            // Has failed to start for some reason - try again
            server.removeConnector(connector)
            connector.stop()
            connector = new SocketConnector()
            connector.port = 0
            server.addConnector(connector)
            connector.start()
        }
        throw new AssertionError("SocketConnector failed to start.");
    }

    void stop() {
        if (sslConnector) {
            sslConnector.stop()
            sslConnector.close()
            server?.removeConnector(sslConnector)
            sslConnector = null
        }
        server?.stop()
        if (connector) {
            server?.removeConnector(connector)
            connector = null
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
        if (server.started) {
            sslConnector.start()
        }
    }

    int getSslPort() {
        sslConnector.localPort
    }

    void expectUserAgent(UserAgentMatcher userAgent) {
        this.expectedUserAgent = userAgent;
    }

    void resetExpectations() {
        try {
            super.resetExpectations()
        } finally {
            realm = null
            expectedUserAgent = null
            collection.setHandlers()
        }
    }

    /**
     * Adds a given file at the given URL. The source file can be either a file or a directory.
     */
    void allowHead(String path, File srcFile) {
        allow(path, true, ['HEAD'], fileHandler(path, srcFile))
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
    void allowGetOrHeadWithRevalidate(String path, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], revalidateFileHandler(path, srcFile))
    }

    /**
     * Adds a given file at the given URL with the given credentials. The source file can be either a file or a directory.
     */
    void allowGetOrHead(String path, String username, String password, File srcFile) {
        allow(path, true, ['GET', 'HEAD'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Allows one GET request for the given URL, which return 404 status code
     */
    void allowGetMissing(String path) {
        allow(path, false, ['GET'], notFound())
    }

    private Action fileHandler(String path, File srcFile) {
        return new SendFileAction(path, srcFile, false)
    }

    private Action revalidateFileHandler(String path, File srcFile) {
        return new SendFileAction(path, srcFile, true)
    }

    class SendFileAction extends ActionSupport {
        private final String path
        private final File srcFile
        private final boolean revalidate

        SendFileAction(String path, File srcFile, boolean revalidate) {
            super("return contents of $srcFile.name")
            this.srcFile = srcFile
            this.path = path
            this.revalidate = revalidate
        }

        void handle(HttpServletRequest request, HttpServletResponse response) {
            if (expectedUserAgent != null) {
                String receivedUserAgent = request.getHeader("User-Agent")
                if (!expectedUserAgent.matches(receivedUserAgent)) {
                    response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent));
                    return;
                }
            }
            if (revalidate) {
                String cacheControl = request.getHeader("Cache-Control")
                if (!cacheControl.equals("max-age=0")) {
                    response.sendError(412, String.format("Precondition Failed: Expected Cache-Control:max-age=0 but was '%s'", cacheControl));
                    return
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
                sendFile(response, file, null, null, interaction.contentType)
            } else if (file.isDirectory()) {
                sendDirectoryListing(response, file)
            } else {
                response.sendError(404, "'$request.pathInfo' does not exist")
            }
        }
    }

    /**
     * Adds a broken resource at the given URL.
     */
    void addBroken(String path) {
        allow(path, true, null, broken())
    }

    /**
     * Expects one GET request, which fails with a 500 status code
     */
    void expectGetBroken(String path) {
        expect(path, false, ['GET'], broken())
    }

    /**
     * Expects one GET request for the given URL, which return 404 status code
     */
    void expectGetMissing(String path, PasswordCredentials passwordCredentials = null) {
        expect(path, false, ['GET'], notFound(), passwordCredentials)
    }

    void allowGetOrHeadMissing(String path) {
        allow(path, false, ['GET', 'HEAD'], notFound())
    }

    /**
     * Expects one HEAD request for the given URL, which return 404 status code
     */
    void expectHeadMissing(String path) {
        expect(path, false, ['HEAD'], notFound())
    }

    /**
     * Expects one HEAD request for the given URL, which returns a 500 status code
     */
    void expectHeadBroken(String path) {
        expect(path, false, ['HEAD'], broken())
    }

    private Action notFound() {
        new ActionSupport("return 404 not found") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(404, "not found")
            }
        }
    }

    private Action broken() {
        new ActionSupport("return 500 broken") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(500, "broken")
            }
        }
    }

    /**
     * Expects one HEAD request for the given URL.
     */
    void expectHead(String path, File srcFile) {
        expect(path, false, ['HEAD'], fileHandler(path, srcFile))
    }

    /**
     * Expects one HEAD request for the given URL, asserting that the request is revalidated.
     */
    void expectHeadRevalidate(String path, File srcFile) {
        expect(path, false, ['HEAD'], revalidateFileHandler(path, srcFile))
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
    HttpResourceInteraction expectGet(String path, File srcFile) {
        return expect(path, false, ['GET'], fileHandler(path, srcFile))
    }

    /**
     * Allows one GET request for the given URL, asserting that the request revalidates. Reads the request content from the given file.
     */
    HttpResourceInteraction expectGetRevalidate(String path, File srcFile) {
        return expect(path, false, ['GET'], revalidateFileHandler(path, srcFile))
    }

    /**
     * Expects one GET request for the given URL, with the given credentials. Reads the request content from the given file.
     */
    HttpResourceInteraction expectGet(String path, String username, String password, File srcFile) {
        return expect(path, false, ['GET'], withAuthentication(path, username, password, fileHandler(path, srcFile)))
    }

    /**
     * Expects one GET request for the given URL, with the response being GZip encoded.
     */
    void expectGetGZipped(String path, File srcFile) {
        expect(path, false, ['GET'], new ActionSupport("return gzipped $srcFile.name") {
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
     * Expects one GET request for the given URL, responding with a redirect.
     */
    void expectGetRedirected(String path, String location) {
        expectRedirected('GET', path, location)
    }

    /**
     * Expects one HEAD request for the given URL, responding with a redirect.
     */
    void expectHeadRedirected(String path, String location) {
        expectRedirected('HEAD', path, location)
    }

    /**
     * Expects one PUT request for the given URL, responding with a redirect.
     */
    void expectPutRedirected(String path, String location) {
        expectRedirected('PUT', path, location)
    }

    private void expectRedirected(String method, String path, String location) {
        expect(path, false, [method], new ActionSupport("redirect to $location") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendRedirect(location)
            }
        })
    }

    /**
     * Allows GET requests for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void allowGetDirectoryListing(String path, File directory) {
        allow(path, false, ['GET'], new ActionSupport("return listing of directory $directory.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        })
    }

    /**
     * Expects one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, File directory) {
        expect(path, false, ['GET'], new ActionSupport("return listing of directory $directory.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        })
    }

    /**
     * Expects one GET request for the given URL, returning an apache-compatible directory listing with the given File names.
     */
    void expectGetDirectoryListing(String path, String username, String password, File directory) {
        expect(path, false, ['GET'], withAuthentication(path, username, password, new ActionSupport("return listing of directory $directory.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        }));
    }

    private sendFile(HttpServletResponse response, File file, Long lastModified, Long contentLength, String contentType) {
        if (sendLastModified) {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified ?: file.lastModified())
        }
        def content = file.bytes
        response.setContentLength((contentLength ?: content.length) as int)
        response.setContentType(contentType ?: new MimeTypes().getMimeByExtension(file.name).toString())
        if (sendSha1Header) {
            response.addHeader("X-Checksum-Sha1", HashUtil.sha1(content).asHexString())
        }

        addEtag(response, content, etags)
        response.outputStream << content
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
        def writer = new StringWriter()
        def markupBuilder = new MarkupBuilder(writer)
        markupBuilder.doubleQuotes = true // for Ivy
        markupBuilder.html {
            for (String fileName : directory.list()) {
                def uri = UrlEscapers.urlPathSegmentEscaper().escape(fileName).replaceAll(':', '%3A')
                a(href: uri, fileName)
            }

        }
        def directoryListing = writer.toString().getBytes("utf8")

        response.setContentLength(directoryListing.length)
        response.setContentType("text/html")
        response.setCharacterEncoding("utf8")
        response.outputStream.bytes = directoryListing
    }

    /**
     * Expects one PUT request for the given URL. Writes the request content to the given file.
     */
    void expectPut(String path, File destFile, int statusCode = HttpStatus.ORDINAL_200_OK, PasswordCredentials credentials = null) {
        def action = new ActionSupport("write request to $destFile.name and return status $statusCode") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (HttpServer.this.expectedUserAgent != null) {
                    String receivedUserAgent = request.getHeader("User-Agent")
                    if (!expectedUserAgent.matches(receivedUserAgent)) {
                        response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent))
                        return;
                    }
                }
                GFileUtils.mkdirs(destFile.parentFile)
                destFile.bytes = request.inputStream.bytes
                response.setStatus(statusCode)
            }
        }

        expect(path, false, ['PUT'], action, credentials)
    }

    /**
     * Expects one PUT request for the given URL, with the given credentials. Writes the request content to the given file.
     */
    void expectPut(String path, String username, String password, File destFile) {
        expect(path, false, ['PUT'], withAuthentication(path, username, password, new ActionSupport("write request to $destFile.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {

                if (request.remoteUser != username) {
                    response.sendError(500, "unexpected username '${request.remoteUser}'")
                    return
                }
                destFile.parentFile.mkdirs()
                destFile.bytes = request.inputStream.bytes
            }
        }))
    }

    /**
     * Allows PUT requests with the given credentials.
     */
    void allowPut(String path, String username, String password) {
        allow(path, false, ['PUT'], withAuthentication(path, username, password, new ActionSupport("return 500") {
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
            @Override
            HttpResourceInteraction getInteraction() {
                return action.interaction
            }

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

    void expect(String path, Collection<String> methods, PasswordCredentials passwordCredentials = null, Action action) {
        expect(path, false, methods, action, passwordCredentials)
    }

    HttpResourceInteraction expect(String path, boolean matchPrefix, Collection<String> methods, Action action, PasswordCredentials credentials = null) {
        if (credentials != null) {
            action = withAuthentication(path, credentials.username, credentials.password, action)
        }

        HttpExpectOne expectation = new HttpExpectOne(action, methods, path)
        expectations << expectation
        add(path, matchPrefix, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (expectation.run) {
                    return
                }
                expectation.run = true
                action.handle(request, response)
                request.handled = true
            }
        })

        return action.interaction
    }

    private void allow(String path, boolean matchPrefix, Collection<String> methods, Action action) {
        add(path, matchPrefix, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                action.handle(request, response)
                request.handled = true
            }
        })
    }

    private void add(String path, boolean matchPrefix, Collection<String> methods, Handler handler) {
        assert path.startsWith('/')
        def prefix = path == '/' ? '/' : path + '/'
        collection.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (methods != null && !methods.contains(request.method)) {
                    return
                }
                boolean match = request.pathInfo == path || (matchPrefix && request.pathInfo.startsWith(prefix))
                if (match && !request.handled) {
                    handler.handle(target, request, response, dispatch)
                }
            }
        })
    }

    void addHandler(Handler handler) {
        collection.addHandler(handler)
    }

    int getPort() {
        return connector.localPort
    }

    static class HttpExpectOne extends ExpectOne {
        final Action action
        final Collection<String> methods
        final String path

        HttpExpectOne(Action action, Collection<String> methods, String path) {
            this.action = action
            this.methods = methods
            this.path = path
        }

        String getNotMetMessage() {
            "Expected HTTP request not received: ${methods.size() == 1 ? methods[0] : methods} $path and $action.displayName"
        }
    }

    interface Action {
        HttpResourceInteraction getInteraction()

        String getDisplayName()

        void handle(HttpServletRequest request, HttpServletResponse response)
    }

    static abstract class ActionSupport implements Action {
        final String displayName
        final HttpResourceInteraction interaction = new DefaultResourceInteraction()

        ActionSupport(String displayName) {
            this.displayName = displayName
        }
    }

    static class DefaultResourceInteraction implements HttpResourceInteraction {
        String contentType

        @Override
        HttpResourceInteraction contentType(String encoding) {
            this.contentType = encoding
            return this
        }
    }

    static class Utils {
        static JsonElement json(HttpServletRequest request) {
            new Gson().fromJson(request.reader, JsonElement.class)
        }

        static JsonElement json(String json) {
            new Gson().fromJson(json, JsonElement.class)
        }

        static void json(HttpServletResponse response, Object data) {
            if (!response.contentType) {
                response.setContentType("application/json")
            }
            StringBuilder sb = new StringBuilder()
            new Gson().toJson(data, sb)
            response.outputStream.withStream {
                it << sb.toString().getBytes("utf8")
            }
        }
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

    public static class HideUnauthorizedBasicAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__BASIC_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new BasicAuthenticator() {
                @Override
                void sendChallenge(UserRealm realm, Response response) throws IOException {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        }
    }

    public static class NtlmAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return NtlmAuthenticator.NTLM_AUTH_METHOD
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new NtlmAuthenticator()
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
