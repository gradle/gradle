/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import org.apache.commons.io.IOUtils
import org.apache.commons.lang.math.RandomUtils
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

class HttpResourceAccessorTest extends Specification {
    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    def "should call close() on CloseableHttpResource when getMetaData is called"() {
        def response = mockHttpResponse()
        def http = Mock(HttpClientHelper) {
            performHead(uri.toString(), _) >> new HttpClientResponse("GET", uri, response)
        }

        when:
        new HttpResourceAccessor(http).getMetaData(name, false)

        then:
        1 * response.close()
    }

    def "when cache position is valid, then perform range request"() {
        SslContextFactory sslContextFactory = new DefaultSslContextFactory()
        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withSslContextFactory(sslContextFactory)
            .withRedirectVerifier({})
            .build()
        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)
        def tempFile = File.createTempFile("spring-core", ".pom", File.createTempDir("http-resource-accessor"))
        def url = "https://repo1.maven.org/maven2/org/springframework/spring-core/6.1.12/spring-core-6.1.12.pom" // 2026 bytes
        def resource = new ExternalResourceName(url.toURI())
        def accessor = new HttpResourceAccessor(client)
        accessor.setChunkSize(100) // 100 bytes

        when:
        accessor.<Void> withContent(resource, true, tempFile, (ExternalResource.ContentAction) (content) -> {
            println "cache saved into " + tempFile.getAbsolutePath()
            return null;
        })

        then:
        assert client.performGet(url, true).content.bytes.length == tempFile.bytes.length
    }

    private CloseableHttpResponse mockHttpResponse() {
        def response = Mock(CloseableHttpResponse)
        def statusLine = Mock(StatusLine)
        statusLine.getStatusCode() >> 200
        response.getStatusLine() >> statusLine
        response
    }

    def "when copy from unstable input stream then partial content is saved"() {
        given:
        def tempFile = File.createTempFile("unstable-input-stream", ".bin", File.createTempDir("http-resource-accessor"))
        def expectedRound = 2
        def chunkSize = 100

        def unstableInputStream = new InputStream() {
            private int round = 0

            @Override
            int read() throws IOException {
                return RandomUtils.nextInt(256)
            }

            @Override
            int read(byte[] b) throws IOException {
                if (round >= expectedRound) {
                    throw new IOException("Oops")
                }
                round++
                return super.read(b)
            }
        }

        when:
        try {
            IOUtils.copy(unstableInputStream, new FileOutputStream(tempFile), chunkSize)
        } catch (IOException ignore) {
        }

        then:
        assert tempFile.length() == expectedRound * chunkSize
    }
}
