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

import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.google.gson.JsonElement
import groovy.xml.MarkupBuilder
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.internal.BiAction
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.server.ExpectOne
import org.gradle.test.fixtures.server.ServerExpectation
import org.gradle.test.fixtures.server.ServerWithExpectations
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GFileUtils
import org.hamcrest.Matcher
import org.mortbay.io.EndPoint
import org.mortbay.jetty.Handler
import org.mortbay.jetty.HttpHeaders
import org.mortbay.jetty.HttpStatus
import org.mortbay.jetty.MimeTypes
import org.mortbay.jetty.Request
import org.mortbay.jetty.handler.AbstractHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * Due to be replaced by {@link BlockingHttpServer}. You should prefer using that class where possible, however there is a bunch of stuff on this fixture that is missing from {@link BlockingHttpServer}.
 */
class HttpServer extends ServerWithExpectations implements HttpServerFixture {

    private final static Logger logger = LoggerFactory.getLogger(HttpServer.class)

    protected Matcher expectedUserAgent = null

    List<ServerExpectation> expectations = []

    boolean chunkedTransfer = false

    org.gradle.api.Action<HttpServletRequest> beforeHandle
    org.gradle.api.Action<HttpServletRequest> afterHandle

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

    void beforeHandle(org.gradle.api.Action<HttpServletRequest> r) {
        beforeHandle = r
    }

    void afterHandle(org.gradle.api.Action<HttpServletRequest> r) {
        afterHandle = r
    }

    @Override
    Handler getCustomHandler() {
        return new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                String d = request.getQueryString()
                if (request.handled) {
                    return
                }
                onFailure(new AssertionError("Received unexpected ${request.method} request to ${target}."))
                response.sendError(404, "'$target' does not exist")
            }
        }
    }

    @Override
    String toString() {
        if (server.started) {
            return "HttpServer " + String.valueOf(getUri())
        } else {
            return "HttpServer (unstarted)"
        }
    }

    protected Logger getLogger() {
        logger
    }

    void expectUserAgent(UserAgentMatcher userAgent) {
        this.expectedUserAgent = userAgent
    }

    void resetExpectations() {
        try {
            super.resetExpectations()
        } finally {
            reset()
            expectedUserAgent = null
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

    protected SendFileAction fileHandler(String path, File srcFile) {
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
            if (beforeHandle) {
                beforeHandle.execute(request)
            }
            try {
                if (expectedUserAgent != null) {
                    String receivedUserAgent = request.getHeader("User-Agent")
                    if (!expectedUserAgent.matches(receivedUserAgent)) {
                        response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent))
                        return
                    }
                }
                if (revalidate) {
                    String cacheControl = request.getHeader("Cache-Control")
                    if (!cacheControl.equals("max-age=0")) {
                        response.sendError(412, String.format("Precondition Failed: Expected Cache-Control:max-age=0 but was '%s'", cacheControl))
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
            } finally {
                if (afterHandle) {
                    afterHandle.execute(request)
                }
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
     *  Expects one GET request, which fails with a 401 status code.
     */
    void expectGetUnauthorized(String path) {
        expect(path, false, ['GET'], unauthorized())
    }

    /**
     * Expects one GET request, which will block for maximum 60 seconds
     */
    void expectGetBlocking(String path) {
        expect(path, false, ['GET'], blocking())
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

    private Action unauthorized() {
        new ActionSupport("return 401 unauthorized") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(401, "unauthorized")
            }
        }
    }

    private Action blocking() {
        new ActionSupport("throw socket timeout exception") {
            CountDownLatch latch = new CountDownLatch(1)

            void handle(HttpServletRequest request, HttpServletResponse response) {
                try {
                    latch.await(60, TimeUnit.SECONDS)
                } catch (InterruptedException e) {
                    // ignore
                }
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
        expect(path, false, ['HEAD'], fileHandler(path, srcFile), new DefaultPasswordCredentials(username, password))
    }

    /**
     * Allows one GET request for the given URL. Reads the request content from the given file.
     */
    HttpResourceInteraction expectGet(String path, File srcFile) {
        return expect(path, false, ['GET'], fileHandler(path, srcFile))
    }

    /**
     * Allows one GET request for the given URL with a query string. Reads the request content from the given file.
     */
    HttpResourceInteraction expectGetWithQueryString(String path, String query, File srcFile) {
        return expect(path, false, ['GET'], withQueryString(query, fileHandler(path, srcFile)))
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
        return expect(path, false, ['GET'], fileHandler(path, srcFile), new DefaultPasswordCredentials(username, password))
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
        })
    }

    /**
     * Expects one GET request for the given URL, responding with a redirect.
     */
    void expectGetRedirected(String path, String location, PasswordCredentials passwordCredentials = null) {
        expectRedirected('GET', path, location, passwordCredentials)
    }

    /**
     * Expects one HEAD request for the given URL, responding with a redirect.
     */
    void expectHeadRedirected(String path, String location, PasswordCredentials passwordCredentials = null) {
        expectRedirected('HEAD', path, location, passwordCredentials)
    }

    /**
     * Expects one PUT request for the given URL, responding with a redirect.
     */
    void expectPutRedirected(String path, String location, PasswordCredentials passwordCredentials = null) {
        expectRedirected('PUT', path, location, passwordCredentials)
    }

    private void expectRedirected(String method, String path, String location, PasswordCredentials credentials) {
        expect(path, false, [method], redirectTo(location), credentials)
    }

    private HttpServer.Action redirectTo(location) {
        new ActionSupport("redirect to $location") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendRedirect(location)
            }
        }
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
        expect(path, false, ['GET'], listDirectory(directory), new DefaultPasswordCredentials(username, password))
    }

    private HttpServer.Action listDirectory(File directory) {
        new ActionSupport("return listing of directory $directory.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                sendDirectoryListing(response, directory)
            }
        }
    }

    private sendFile(HttpServletResponse response, File file, Long lastModified, Long contentLength, String contentType) {
        if (sendLastModified) {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified ?: file.lastModified())
        }
        def content = file.bytes

        if (chunkedTransfer) {
            response.setHeader("Transfer-Encoding", "chunked")
        } else {
            response.setContentLength((contentLength ?: content.length) as int)
        }

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
    void expectPut(String path, File destFile, int statusCode = HttpStatus.ORDINAL_200_OK, PasswordCredentials credentials = null, long expectedContentLength = -1) {
        def action = new ActionSupport("write request to $destFile.name (content length: $expectedContentLength) and return status $statusCode") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (HttpServer.this.expectedUserAgent != null) {
                    String receivedUserAgent = request.getHeader("User-Agent")
                    if (!expectedUserAgent.matches(receivedUserAgent)) {
                        response.sendError(412, String.format("Precondition Failed: Expected User-Agent: '%s' but was '%s'", expectedUserAgent, receivedUserAgent))
                        return
                    }
                }
                if (expectedContentLength > -1) {
                    if (request.contentLength != expectedContentLength) {
                        response.sendError(412, String.format("Precondition Failed: Expected Content-Length: '%d' but was '%d'", expectedContentLength, request.contentLength))
                        return
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
        expect(path, false, ['PUT'], fileWriter(destFile), new DefaultPasswordCredentials(username, password))
    }

    private Action fileWriter(File destFile) {
        new ActionSupport("write request to $destFile.name") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                destFile.parentFile.mkdirs()
                destFile.bytes = request.inputStream.bytes
            }
        }
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
        requireAuthentication(path, username, password)

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

    private static Action withQueryString(String query, Action action) {
        return new Action() {
            @Override
            HttpResourceInteraction getInteraction() {
                return action.interaction
            }

            String getDisplayName() {
                return action.displayName
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                assert request.queryString == query
                action.handle(request, response)
            }
        }
    }

    private static Action withLenientQueryString(String query, Action action) {
        return new Action() {
            @Override
            HttpResourceInteraction getInteraction() {
                return action.interaction
            }

            String getDisplayName() {
                return action.displayName
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (request.queryString.startsWith(query)) {
                    action.handle(request, response)
                }
            }
        }
    }

    void expect(String path, Collection<String> methods, PasswordCredentials passwordCredentials = null, Action action) {
        expect(path, false, methods, action, passwordCredentials)
    }

    HttpResourceInteraction expect(String path, boolean matchPrefix, Collection<String> methods, Action action, PasswordCredentials credentials = null) {
        if (credentials != null) {
            action = withAuthentication(path, credentials.username, credentials.password, action)
        } else {
            action = refuseAuthentication(path, action)
        }

        HttpExpectOne expectation = new HttpExpectOne(action, methods, path)
        expectations << expectation
        add(path, matchPrefix, methods, new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (expectation.atomicRun.getAndSet(true)) {
                    return
                }
                action.handle(request, response)
                request.handled = true
            }
        })

        return action.interaction
    }

    private Action refuseAuthentication(String path, Action action) {
        new Action() {
            @Override
            HttpResourceInteraction getInteraction() {
                return action.interaction
            }

            String getDisplayName() {
                return action.displayName
            }

            void handle(HttpServletRequest request, HttpServletResponse response) {

                if (authenticationScheme.handler.containsUnexpectedAuthentication(request)) {
                    response.sendError(500, "unexpected authentication in headers ")
                    return
                }
                action.handle(request, response)
            }
        }
    }

    protected void allow(String path, boolean matchPrefix, Collection<String> methods, Action action) {
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

    /**
     * Blocks on SSL handshake for 60 seconds.
     */
    void expectSslHandshakeBlocking() {
        sslPreHandler = new BiAction<EndPoint, Request>() {
            @Override
            void execute(EndPoint endPoint, Request request) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(60))
            }
        }
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

    protected static abstract class ActionSupport implements Action {
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
}
