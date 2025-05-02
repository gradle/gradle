/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resource.s3.fixtures


import groovy.xml.StreamingMarkupBuilder
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.gradle.integtests.resource.s3.fixtures.stub.HttpStub
import org.gradle.integtests.resource.s3.fixtures.stub.StubRequest
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.internal.TextUtil
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.tz.FixedDateTimeZone

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.security.MessageDigest

class S3Server extends HttpServer implements RepositoryServer {

    public static final String BUCKET_NAME = "tests3bucket"
    private static final DateTimeZone GMT = new FixedDateTimeZone("GMT", "GMT", 0, 0)
    protected static final DateTimeFormatter RCF_822_DATE_FORMAT = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
        .withLocale(Locale.US)
        .withZone(GMT);

    public static final String ETAG = 'd41d8cd98f00b204e9800998ecf8427e'
    public static final String X_AMZ_REQUEST_ID = '0A398F9A1BAD4027'
    public static final String X_AMZ_ACL = 'bucket-owner-full-control'
    public static final String X_AMZ_ID_2 = 'nwUZ/n/F2/ZFRTZhtzjYe7mcXkxCaRjfrJSWirV50lN7HuvhF60JpphwoiX/sMnh'
    public static final String X_AMZ_UPLOAD_ID = 'VXBsb2FkIElEIGZvciA2aWWpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA'
    public static final String DATE_HEADER = 'Mon, 29 Sep 2014 11:04:27 GMT'
    public static final String SERVER_AMAZON_S3 = 'AmazonS3'

    TestDirectoryProvider testDirectoryProvider

    S3Server(TestDirectoryProvider testDirectoryProvider) {
        super()
        this.testDirectoryProvider = testDirectoryProvider;
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
        assert stubRequest.headers["x-amz-acl"] == request.getHeader("x-amz-acl")
        assert stubRequest.method == request.method
        assert stubRequest.params.every {
            request.getParameterMap()[it.key] == it.value
        }
    }

    boolean requestMatches(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        assert path.startsWith('/')
        boolean result = path == request.pathInfo && stubRequest.method == request.method &&
            stubRequest.params.every { request.getParameterMap().containsKey(it.key) } &&
            request.getParameterMap().every { stubRequest.params.containsKey(it.key) }
        result
    }

    @Override
    IvyS3Repository getRemoteIvyRepo() {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME)
    }

    @Override
    IvyS3Repository getRemoteIvyRepo(boolean m2Compatible, String dirPattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern)
    }

    @Override
    IvyS3Repository getRemoteIvyRepo(boolean m2Compatible, String dirPattern, String ivyFilePattern, String artifactFilePattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    @Override
    IvyS3Repository getRemoteIvyRepo(String contextPath) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME$contextPath"), "$contextPath", BUCKET_NAME)
    }

    @Override
    String getValidCredentials() {
        return """
        credentials(AwsCredentials) {
            accessKey = "someKey"
            secretKey = "someSecret"
        }"""
    }

    def stubPutFile(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = url
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive',
                    'x-amz-acl': X_AMZ_ACL,
                ]
                body = { InputStream content ->
                    file.parentFile.mkdirs()
                    file.bytes = content.bytes
                }
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    "ETag": { calculateEtag(file) },
                    'Server': SERVER_AMAZON_S3
                ]
            }
        }
        expect(httpStub)
    }

    def stubMultipartUpload(String bucketName, String keyName, File file) {
        stubInitiateMultipartUpload(bucketName, keyName)
        stubUploadPart(bucketName, keyName, file)
        stubCompleteMultipartUpload(bucketName, keyName, file)
    }

    def stubInitiateMultipartUpload(String bucketName, String keyName) {
        def xml = new StreamingMarkupBuilder().bind {
            InitiateMultipartUploadResult(xmlns: "http://s3.amazonaws.com/doc/2006-03-01/") {
                Bucket(bucketName)
                Key(keyName)
                UploadId(X_AMZ_UPLOAD_ID)
            }
        }
        def length = xml.toString().size()
        def url = "/${bucketName}/${keyName}"
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'POST'
                path = url
                params = ['uploads': ['']]
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Length': length,
                    'Last-Modified': RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
                body = { xml.toString() }
            }
        }
        expect(httpStub)
    }

    def stubUploadPart(String bucketName, String keyName, File file) {
        def url = "/${bucketName}/${keyName}"
        def length = file.size()
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = url
                params = [
                    'partNumber': ['1'],
                    'uploadId': [X_AMZ_UPLOAD_ID]
                ]
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive',
                    'Content-Length': length
                ]
                body = { InputStream content ->
                    file.parentFile.mkdirs()
                    file.bytes = content.bytes
                }
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'ETag': calculateEtag(file),
                    'Content-Length': 0,
                    'Last-Modified': RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
            }
        }
        expect(httpStub)
    }

    def stubCompleteMultipartUpload(String bucketName, String keyName, File file) {
        def requestXml = new StreamingMarkupBuilder().bind {
            CompleteMultipartUpload(xmlns: "http://s3.amazonaws.com/doc/2006-03-01/") {
                Part() {
                    PartNumber(1)
                    ETag(calculateEtag(file))
                }
            }
        }
        def url = "/${bucketName}/${keyName}"
        def responseXml = new StreamingMarkupBuilder().bind {
            CompleteMultipartUploadResult(xmlns: "http://s3.amazonaws.com/doc/2006-03-01/") {
                Location(url)
                Bucket(bucketName)
                Key(keyName)
                ETag(calculateEtag(file))
            }
        }
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'POST'
                path = url
                params = ['uploadId': [X_AMZ_UPLOAD_ID]]
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive',
                ]
                body = { requestXml.toString() }
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Last-Modified': RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
                body = { responseXml.toString() }
            }
        }
        expect(httpStub)
    }

    def stubMetaData(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'ETag': { calculateEtag(file) },
                    'Server': SERVER_AMAZON_S3,
                    'Accept-Ranges': 'bytes',
                    'Content-Type': 'application/octet-stream',
                    'Content-Length': "0",
                    'Last-Modified': RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
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
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = statusCode
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]
            }
        }
        expect(httpStub)
    }

    def stubGetFile(File file, String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'ETag': { calculateEtag(file) },
                    'Server': SERVER_AMAZON_S3,
                    'Accept-Ranges': 'bytes',
                    'Content-Type': 'application/octet-stream',
                    'Content-Length': { file.length() },
                    'Last-Modified': RCF_822_DATE_FORMAT.print(new Date().getTime())
                ]
                body = { file.bytes }
            }
        }
        expect(httpStub)
    }

    def stubListFile(File file, String bucketName, prefix = 'maven/release/') {
        def files = file.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.isFile()
            }
        })

        def dirs = file.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.isDirectory()
            }
        })

        def xml = new StreamingMarkupBuilder().bind {
            ListBucketResult(xmlns: "http://s3.amazonaws.com/doc/2006-03-01/") {
                Name(bucketName)
                Prefix(prefix)
                Marker()
                MaxKeys('1000')
                Delimiter('/')
                IsTruncated('false')
                files.each { currentFile ->
                    Contents {
                        Key(prefix + currentFile.name)
                        LastModified('2014-10-01T13:03:29.000Z')
                        ETag(ETAG)
                        Size(currentFile.length())
                        Owner {
                            ID("${(1..57).collect { 'a' }.join()}")
                            DisplayName('me')
                        }
                        StorageClass('STANDARD')
                    }
                }

                CommonPrefixes {
                    dirs.each { File dir ->
                        String path = TextUtil.normaliseFileSeparators(dir.absolutePath)
                        Prefix("${prefix}${dir.name}/")
                    }
                }
            }
        }

        println(xml.toString())
        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = "/${bucketName}/"
                headers = [
                    'Content-Type': "application/x-www-form-urlencoded; charset=utf-8",
                    'Connection': 'Keep-Alive'
                ]
                params = [
                    'prefix': [prefix],
                    'delimiter': ['/'],
                    'max-keys': ["1000"],
                    'encoding-type': ['url']
                ]
            }
            response {
                status = 200
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]

                body = { xml.toString() }
            }
        }
        expect(httpStub)
    }

    def stubGetFileAuthFailure(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("InvalidAccessKeyId")
                Message("The AWS Access Key Id you provided does not exist in our records.")
                AWSAccessKeyId("notRelevant")
                RequestId("stubbedAuthFailureRequestId")
                HostId("stubbedAuthFailureHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 403
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]
                String thing = xml.toString()
                body = { thing }
            }
        }
        expect(httpStub)
    }

    def stubPutFileAuthFailure(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("InvalidAccessKeyId")
                Message("The AWS Access Key Id you provided does not exist in our records.")
                AWSAccessKeyId("notRelevant")
                RequestId("stubbedAuthFailureRequestId")
                HostId("stubbedAuthFailureHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'PUT'
                path = url
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive',
                    'x-amz-acl': X_AMZ_ACL,
                ]
            }
            response {
                status = 403
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]
                body = { xml.toString() }
            }
        }
        expect(httpStub)
    }

    def stubFileNotFound(String url) {
        def xml = new StreamingMarkupBuilder().bind {
            Error() {
                Code("NoSuchKey")
                Message("The specified key does not exist.")
                Key(url)
                RequestId("stubbedRequestId")
                HostId("stubbedHostId")
            }
        }

        HttpStub httpStub = HttpStub.stubInteraction {
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 404
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]
                body = { xml.toString() }
            }
        }
        expect(httpStub)
    }

    def stubGetFileBroken(String url) {
        HttpStub httpStub = HttpStub.stubInteraction {
            def xml = new StreamingMarkupBuilder().bind {
                Error() {
                    Code("Internal Server Error")
                    Message("Something went seriously wrong")
                    Key(url)
                    RequestId("stubbedRequestId")
                    HostId("stubbedHostId")
                }
            }
            request {
                method = 'GET'
                path = url
                headers = [
                    'Content-Type': 'application/octet-stream',
                    'Connection': 'Keep-Alive'
                ]
            }
            response {
                status = 500
                headers = [
                    'x-amz-id-2': X_AMZ_ID_2,
                    'x-amz-request-id': X_AMZ_REQUEST_ID,
                    'Date': DATE_HEADER,
                    'Server': SERVER_AMAZON_S3,
                    'Content-Type': 'application/xml',
                ]
                body = { xml.toString() }
            }

        }
        expect(httpStub)
    }

    private expect(HttpStub httpStub) {
        add(httpStub, stubAction(httpStub))
    }

    private HttpServer.ActionSupport stubAction(HttpStub httpStub) {
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
                    if (!((Request) request).isHandled()) {
                        expectation.atomicRun.set(true)
                        action.handle(request, response)
                        ((Request) request).setHandled(true)
                    }
                }
            }
        })
    }


    private calculateEtag(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(file.bytes);
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }
}
