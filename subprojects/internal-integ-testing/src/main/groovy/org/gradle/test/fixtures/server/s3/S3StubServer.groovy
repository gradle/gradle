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

package org.gradle.test.fixtures.server.s3

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.stub.HttpStub
import org.gradle.test.fixtures.server.stub.StubRequest
import org.gradle.test.fixtures.server.stub.StubResponse
import org.mortbay.jetty.Request
import org.mortbay.jetty.handler.AbstractHandler

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class S3StubServer extends HttpServer implements RepositoryServer, LocalStorage {

    public static final String BUCKET_NAME = "tests3bucket"
    TestDirectoryProvider testDirectoryProvider
    HttpServer.ActionSupport localStorageResponseAction
    boolean logEnabled = false
    def stubs = []
    final S3StubResponses s3StubResponses

    S3StubServer(TestDirectoryProvider testDirectoryProvider) {
        super()
        this.testDirectoryProvider = testDirectoryProvider
        localStorageResponseAction = localStorageResponseAction()
        s3StubResponses = new S3StubResponses()
    }

    def expect(HttpStub httpStub) {
        add(httpStub, stubAction(httpStub))

    }

    HttpServer.ActionSupport stubAction(HttpStub httpStub) {
        //HttpStub's without a response rely on the server having some behavior (getting and putting files)
        httpStub.response ? stubbedResponseAction(httpStub) : localStorageResponseAction
    }

    private HttpServer.ActionSupport stubbedResponseAction(HttpStub httpStub) {
        new HttpServer.ActionSupport("Generic stub handler") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                handleResponse(response, request, httpStub.response)
            }
        }
    }

    private HttpServer.ActionSupport localStorageResponseAction() {
        new HttpServer.ActionSupport("Storage backed stub handler") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                String path = request.pathInfo
                String method = request.method.toUpperCase()
                if (method == 'PUT') {
                    def file = writeFileFromRequest(request)
                    handleResponse(response, request, s3StubResponses.responseForPutFile(file))
                } else if (method == 'GET') {
                    def file = getFile(request)
                    if (file.exists()) {
                        handleResponse(response, request, s3StubResponses.responseForGetFile(file))
                    } else {
                        handleResponse(response, request, s3StubResponses.responseForGetFileNotFound(path))
                    }
                } else if (method == 'HEAD') {
                    def file = getFile(request)
                    if (file.exists()) {
                        handleResponse(response, request, s3StubResponses.responseForHeadFile(file))
                    } else {
                        handleResponse(response, request, s3StubResponses.responseForHeadFileNotFound(path))
                    }
                }
            }
        }
    }

    private void add(HttpStub httpStub, HttpServer.ActionSupport action) {
        HttpExpectOne expectation = new HttpExpectOne(action, [httpStub.request.method], httpStub.request.path)
        expectations << expectation
        addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {

                if (requestMatches(httpStub, request)) {
                    assertRequest(httpStub, request)
                    if (expectation.run) {
                        return
                    }
                    if (!((Request) request).isHandled()) {
                        expectation.run = true
                        action.handle(request, response)
                        ((Request) request).setHandled(true)
                    } else{
                        log("The request[${request.method} :${request.pathInfo}] was already handeled.")
                    }
                }
            }
        })
        stubs << httpStub
    }

    void assertRequest(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String stubPath = stubRequest.path
        assert stubPath.startsWith('/')
        def incomingPath = request.pathInfo
        assert doesPathMatch(incomingPath, stubPath)
        assert stubRequest.method == request.method
        if (stubRequest.body) {
            assert stubRequest.body == request.getInputStream().bytes
        }
        assert stubRequest.params.every {
            request.getParameterMap()[it.key] == it.value
        }
    }

    boolean requestMatches(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        String incoming = request.pathInfo
        assert path.startsWith('/')
        if (stubRequest.method == request.method) {
            return doesPathMatch(incoming, path)
        }
        return false
    }

    boolean doesPathMatch(String inbound, String expected) {
        if (inbound == expected) {
            log("String equality path match:[inbound:$inbound|expected:$expected]")
            return true
        } else if (inbound ==~ /$expected/) {
            log("Regular expression path match:[inbound:$inbound|expected:$expected]")
            return true
        }
        return false
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo() {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern, String ivyFilePattern, String artifactFilePattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(String contextPath) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME$contextPath"), "$contextPath", BUCKET_NAME)
    }

    @Override
    String getValidCredentials() {
        return """
        credentials(AwsCredentials) {
            accessKey "someKey"
            secretKey "someSecret"
        }"""
    }

    @Override
    File getStorageDirectory() {
        return testDirectoryProvider.testDirectory
    }

    private void handleResponse(HttpServletResponse response, HttpServletRequest request, StubResponse stubResponse) {
        stubResponse.headers?.each {
            response.addHeader(it.key, it.value)
        }
        response.setStatus(stubResponse.status)

        def body = stubResponse.body
        if (body) {
            response.outputStream.bytes = body
        }
    }

    private log(String message) {
        if (logEnabled) {
            println "${new Date()} - $message"
        }
    }

    @Override
    def printHistory() {
        println "Server requests:"
        super.printHistory()
        println "Stubbed requests:"
        stubs.each {
            println "${it.request.method}: ${it.request.path}"
        }
    }
}
