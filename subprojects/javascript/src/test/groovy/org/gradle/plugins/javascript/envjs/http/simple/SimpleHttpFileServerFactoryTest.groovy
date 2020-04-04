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

package org.gradle.plugins.javascript.envjs.http.simple

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class SimpleHttpFileServerFactoryTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())
    TestFile root

    def setup() {
        root = tmp.createDir("content")
    }

    def "can serve content"() {
        given:

        root.file("index.html") << "Some content here"

        when:
        def factory = new SimpleHttpFileServerFactory()
        def server = factory.start(root, 0)

        then:
        server.port != 0

        when:
        HttpURLConnection resource = new URL(server.getResourceUrl("index.html")).openConnection() as HttpURLConnection

        then:
        resource.getHeaderField("Content-Type") == "text/html"
        resource.getResponseCode() == 200
        resource.getHeaderField("Content-Encoding") == Charset.defaultCharset().name()
        resource.content.text == "Some content here"

        cleanup:
        server?.stop()
    }

    def "serves 404"() {
        when:
        def factory = new SimpleHttpFileServerFactory()
        def server = factory.start(root, 0)

        then:
        server.port != 0

        when:
        HttpURLConnection resource = new URL(server.getResourceUrl("index.html")).openConnection() as HttpURLConnection
        resource.content.text

        then:
        thrown FileNotFoundException

        cleanup:
        server?.stop()
    }

    def "The status line of 404 has 'Not Found' as the message"() {
        when:
        def factory = new SimpleHttpFileServerFactory()
        def server = factory.start(root, 0)

        then:
        server.port != 0

        when:
        HttpURLConnection resource = new URL(server.getResourceUrl("index.html")).openConnection() as HttpURLConnection

        then:
        resource.getResponseMessage() == 'Not Found'
        resource.getResponseCode() == 404

        cleanup:
        server?.stop()
    }
}
