/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resource.gcs.fixtures

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.gradle.integtests.resource.gcs.fixtures.stub.HttpStub
import org.gradle.integtests.resource.gcs.fixtures.stub.StubRequest
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.HttpServer
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.tz.FixedDateTimeZone

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.security.MessageDigest

import static org.apache.commons.codec.binary.Base64.encodeBase64String

class GcsServer extends HttpServer implements RepositoryServer {

    private static final String BUCKET_NAME = "testgcsbucket"
    private static final DateTimeZone GMT = new FixedDateTimeZone("GMT", "GMT", 0, 0)
    private static final DateTimeFormatter RCF_3339_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withLocale(Locale.US)
        .withZone(GMT)

    private static final String DATE_HEADER = 'Mon, 29 Sep 2014 11:04:27 GMT'
    private static final String SERVER_GCS = 'GCS'

    TestDirectoryProvider testDirectoryProvider

    GcsServer(TestDirectoryProvider testDirectoryProvider) {
        super()
        this.testDirectoryProvider = testDirectoryProvider
    }

    @Override
    protected void before() {
        start()
    }

    void assertRequest(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        assert path.startsWith('/')
        assert path == request.pathInfo
        assert stubRequest.method == request.method
        assert stubRequest.params.every {
            request.getParameterMap()[it.key] == it.value
        }
    }

    boolean requestMatches(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        assert path.startsWith('/')
        boolean result = path == request.pathInfo && stubRequest.method == request.method
        result
    }

    @Override
    IvyGcsRepository getRemoteIvyRepo() {
        new IvyGcsRepository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME)
    }

    @Override
    IvyGcsRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern) {
        new IvyGcsRepository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern)
    }

    @Override
    IvyGcsRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern, String ivyFilePattern, String artifactFilePattern) {
        new IvyGcsRepository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    @Override
    IvyGcsRepository getRemoteIvyRepo(String contextPath) {
        new IvyGcsRepository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME$contextPath"), "$contextPath", BUCKET_NAME)
    }

    @Override
    String getValidCredentials() {
        return null
    }

    def stubPutFile(File file, String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def uploadLocation = "/${UUID.randomUUID()}"

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'POST'
                path = "/upload/b/$bucketName/o"
                headers = [
                    'Content-Type': 'application/json; charset=utf-8',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Location'        : "$uri$uploadLocation"
                ]
            }
        }
        expect(httpStub)

        httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = uploadLocation
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection'  : 'Keep-Alive'
                ]
                body = { InputStream content ->
                    file.parentFile.mkdirs()
                    file.bytes = content.bytes
                }
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    "ETag"            : { calculateEtag(file) },
                    'Server'          : SERVER_GCS
                ]
                body = { '{}' }
            }
        }
        expect(httpStub)
    }

    def stubMetaData(File file, String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/b/$bucketName/o/$objectName"
                headers = [
                    'Content-Type': 'application/json; charset=utf-8',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Accept-Ranges'   : 'bytes',
                    'Content-Type'    : 'application/json; charset=utf-8',
                ]
                body = {
                    """
                    {
                        "etag": "${calculateEtag(file)}",
                        "size": "0",
                        "updated": "${RCF_3339_DATE_FORMAT.print(file.lastModified())}",
                        "md5Hash": "${encodeBase64String(Hashing.md5().hashFile(file).toByteArray())}"
                    }
                    """
                }
            }
        }
        expect(httpStub)
    }

    def stubMetaDataBroken(String url) {
        stubMetaDataLightWeightGet(url, 500)
    }

    def stubMetaDataMissing(String url) {
        stubFileNotFound(url)
    }

    private stubMetaDataLightWeightGet(String url, int statusCode) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/b/$bucketName/o/$objectName"
                headers = [
                    'Content-Type': 'application/json; charset=utf-8',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = statusCode
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Content-Type': 'application/json; charset=utf-8',
                ]
                body = { '{}' }
            }
        }
       expect(httpStub)
    }

    def stubGetFile(File file, String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/b/$bucketName/o/$objectName"
                headers = [
                    'Content-Type':  'application/json; charset=utf-8',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Accept-Ranges'   : 'bytes',
                    'Content-Type'    : 'application/json; charset=utf-8',
                ]
                body = {
                    """
                    {
                        "etag": "${calculateEtag(file)}",
                        "size": "${file.length()}",
                        "bucket": "${bucketName}",
                        "name": "${objectName}",
                        "updated": "${RCF_3339_DATE_FORMAT.print(file.lastModified())}"
                    }
                    """
                }
            }
        }
        expect(httpStub)

        httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/download/b/$bucketName/o/$objectName"
                headers = [
                    'Content-Type':  'application/octet-stream',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Accept-Ranges'   : 'bytes',
                    'Content-Type'    : 'application/octet-stream',
                ]
                body = { file.bytes }
            }
        }
        expect(httpStub)
    }

    def stubListFile(File file, String bucketName, prefix = 'maven/release/') {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/b/${bucketName}/o"
                params = [
                    'prefix' : [prefix] as String[]
                ]
                headers = [
                    'Content-Type': 'application/json; charset=utf-8',
                    'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'Date'            : DATE_HEADER,
                    'Server'          : SERVER_GCS,
                    'Content-Type'    : 'application/json; charset=utf-8',
                ]
                body = {
                    """
                    {
                        "kind": "storage#objects",
                        "prefixes": ["$prefix"],
                        "items": [${ file.listFiles().collect { currentFile -> """{ "name": "${currentFile.name}" }"""}.join(',') }]
                    }
                    """
                }
            }
        }
        expect(httpStub)
    }

    def stubGetFileAuthFailure(String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path =  "/b/$bucketName/o/$objectName"
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 401
                headers = [
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_GCS,
                        'Content-Type'    : 'text/plain; charset=UTF-8',
                ]
            }
        }
        expect(httpStub)
    }

    def stubPutFileAuthFailure(String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'POST'
                path = "/upload/b/$bucketName/o"
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 403
                headers = [
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_GCS,
                        'Content-Type'    : 'text/plain; charset=UTF-8',
                ]
            }
        }
        expect(httpStub)
    }

    def stubFileNotFound(String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path =  "/b/$bucketName/o/$objectName"
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 404
                headers = [
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_GCS,
                        'Content-Type'    : 'text/plain; charset=UTF-8',
                ]
            }
        }
        expect(httpStub)
    }

    def stubGetFileBroken(String url) {
        def urlParts = urlParts(url)
        def bucketName = urlParts.first
        def objectName = urlParts.rest

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/b/$bucketName/o/$objectName"
                headers = [
                        'Content-Type': 'application/octet-stream',
                        'Connection'  : 'Keep-Alive'
                ]
            }
            response {
                status = 500
                headers = [
                        'Date'            : DATE_HEADER,
                        'Server'          : SERVER_GCS,
                        'Content-Type'    : 'text/plain; charset=UTF-8',
                ]
            }

        }
        expect(httpStub)
    }

    private expect(HttpStub httpStub) {
        add(httpStub, stubAction(httpStub))
    }

    private static HttpServer.ActionSupport stubAction(HttpStub httpStub) {
        new HttpServer.ActionSupport("Generic stub handler") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (httpStub.request.body) {
                    httpStub.request.body.call(request.getInputStream())
                }
                httpStub.response?.headers?.each {
                    response.addHeader(it.key, it.value instanceof Closure ? it.value.call().toString() : it.value.toString())
                }
                response.setStatus(httpStub.response.status)
                if (httpStub.response?.body) {
                    response.outputStream.bytes = httpStub.response.body.call()
                }
            }
        }
    }

    private void add(HttpStub httpStub, HttpServer.ActionSupport action) {
        HttpServer.HttpExpectOne expectation = new HttpServer.HttpExpectOne(action, [httpStub.request.method], httpStub.request.path)
        expectations << expectation
        addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

                if (requestMatches(httpStub, request)) {
                    assertRequest(httpStub, request)
                    if (expectation.run) {
                        println("This expectation for the request [${request.method} :${request.pathInfo}] was already handled - skipping")
                        return
                    }
                    if (!baseRequest.isHandled()) {
                        expectation.atomicRun.set(true)
                        action.handle(request, response)
                        baseRequest.setHandled(true)
                    }
                }
            }

        })
    }

    private static calculateEtag(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(file.bytes)
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }

    static def urlParts(String url) {
        def parts = url.split('/') - ''
        def first = parts.first()
        def rest = (parts - first).join('/')
        return [ first: first, rest: rest ]
    }
}
