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

import groovy.io.FileType
import org.apache.commons.io.IOUtils
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.tz.FixedDateTimeZone
import org.junit.rules.ExternalResource
import org.mortbay.jetty.Handler
import org.mortbay.jetty.Request
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.security.MessageDigest

class S3FileBackedServer extends ExternalResource {
    private File baseDir
    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()
    public static final String X_AMZ_REQUEST_ID = '0A398F9A1BAD4027'
    public static final String X_AMZ_ID_2 = 'nwUZ/n/F2/ZFRTZhtzjYe7mcXkxCaRjfrJSWirV50lN7HuvhF60JpphwoiX/sMnh'
    public static final String DATE_HEADER = 'Mon, 29 Sep 2014 11:04:27 GMT'
    public static final String SERVER_AMAZON_S3 = 'AmazonS3'
    private static final DateTimeZone GMT = new FixedDateTimeZone("GMT", "GMT", 0, 0)
    protected static final DateTimeFormatter RCF_822_DATE_FORMAT = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
            .withLocale(Locale.US)
            .withZone(GMT);

    S3FileBackedServer(File baseDir) {
        this.baseDir = new File(baseDir.absolutePath + '/s3')
        baseDir.mkdir()
        addHandlers()
        server.start()
    }

    def addHandlers() {
        HandlerCollection handlers = new HandlerCollection()
        handlers.addHandler(putFileHandler())
        handlers.addHandler(getFileHandler())
        handlers.addHandler(getFileMetaDataHandler())
        handlers.addHandler(collection)
        server.setHandlers(handlers)
    }

    URI getUri() {
        if (!server.started) {
            server.start()
        }
        new URI("http://localhost:${port}")
    }

    int getPort() {
        return server.connectors[0].localPort
    }

    Handler getFileHandler() {
        new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.method == 'GET') {
                    File file = getFile(request)
                    if (file.exists()) {
                        [
                                'x-amz-id-2'      : X_AMZ_ID_2,
                                'x-amz-request-id': X_AMZ_REQUEST_ID,
                                'Date'            : DATE_HEADER,
                                'ETag'            : calculateEtag(file),
                                'Server'          : SERVER_AMAZON_S3,
                                'Accept-Ranges'   : 'bytes',
                                'Content-Type'    : 'application/octet-stream',
                                'Content-Length'  : "${file.length()}",
                                'Last-Modified'   : RCF_822_DATE_FORMAT.print(file.lastModified())
                        ].each {
                            response.addHeader(it.key, it.value)
                        }
                        response.outputStream << file.getBytes()
                        response.status = 200
                        ((Request) request).setHandled(true)
                    }
                    println("handling http request: $request.method $target")
                }
            }
        }
    }

    Handler getFileMetaDataHandler() {
        new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.method == 'HEAD') {
                    File file = getFile(request)
                    if (file.exists()) {
                        [
                                'x-amz-id-2'      : X_AMZ_ID_2,
                                'x-amz-request-id': X_AMZ_REQUEST_ID,
                                'Date'            : DATE_HEADER,
                                'ETag'            : calculateEtag(file),
                                'Server'          : SERVER_AMAZON_S3,
                                'Accept-Ranges'   : 'bytes',
                                'Content-Type'    : 'application/octet-stream',
                                'Content-Length'  : "${file.length()}",
                                'Last-Modified'   : RCF_822_DATE_FORMAT.print(file.lastModified())
                        ].each {
                            response.addHeader(it.key, it.value)
                        }
                        response.status = 200
                        ((Request) request).setHandled(true)
                    }
                    println("handling http request: $request.method $target")
                }
            }
        }
    }

    Handler putFileHandler() {
        new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.method == 'PUT') {
                    println("handling http request: $request.method $target")
                    File f = writeFileFromRequest(request)

                    [
                            'x-amz-id-2'      : X_AMZ_ID_2,
                            'x-amz-request-id': X_AMZ_REQUEST_ID,
                            'Date'            : DATE_HEADER,
                            "ETag"            : calculateEtag(f),
                            'Server'          : SERVER_AMAZON_S3].each {
                        response.addHeader(it.key, it.value)
                    }
                    response.setStatus(200)
                    ((Request) request).setHandled(true)
                }
            }

        }
    }

    public List<File> getContents() {
        def list = []
        def dir = baseDir
        dir.eachFileRecurse(FileType.FILES) { file ->
            list << file
        }
        return list
    }

    private File writeFileFromRequest(HttpServletRequest request) {
        File f = new File("${baseDir.getAbsolutePath()}${request.pathInfo}")
        def parent = f.getParentFile()
        if (!parent.exists()) {
            parent.mkdirs()
        }
        f.createNewFile()
        def inStream = request.getInputStream()
        def out = f.newOutputStream()
        try {
            IOUtils.copy(inStream, out)
            println "   Wrote file ${f.getAbsolutePath()} to disk"
        } catch (IOException e) {
            throw e
        } finally {
            out.close()
        }
        f
    }

    File getFile(HttpServletRequest request) {
        new File("${baseDir.getAbsolutePath()}${request.pathInfo}")
    }

    def calculateEtag(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(file.bytes);
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }

    @Override
    protected void after() {
        super.after()
        server.stop()
        println("Stopping S3FileBackedServer server")
    }
}
