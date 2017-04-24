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

package org.gradle.test.fixtures.server.http

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class BlockingHttpServerTest extends ConcurrentSpec {
    def server = new BlockingHttpServer(500)

    def "succeeds when expected serial requests are made"() {
        given:
        server.expectSerialExecution("a")
        server.expectSerialExecution("b")
        server.expectSerialExecution("c")
        server.start()

        when:
        server.uri("a").toURL().text
        server.uri("b").toURL().text
        server.uri("c").toURL().text
        server.stop()

        then:
        noExceptionThrown()
    }

    def "succeeds when expected concurrent requests are made"() {
        given:
        server.expectConcurrentExecution("a", "b", "c")
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("b").toURL().text }
            start { server.uri("c").toURL().text }
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "fails on stop when expected serial request is not made"() {
        given:
        server.expectSerialExecution("a")
        server.expectSerialExecution("b")
        server.start()

        when:
        server.uri("a").toURL().text
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Did not receive expected requests. Waiting for [b], received []'
    }

    def "fails when request is received after serial expectations met"() {
        given:
        server.expectSerialExecution("a")
        server.start()

        when:
        server.uri("a").toURL().text
        server.uri("a").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Received unexpected request GET /a'
    }

    def "fails when request path does not match expected serial request"() {
        given:
        server.expectSerialExecution("a")
        server.start()

        when:
        server.uri("b").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Failed to handle GET /b'
    }

    def "fails when request method does not match expected serial request"() {
        given:
        server.expectSerialExecution("a")
        server.start()

        when:
        def connection = server.uri("a").toURL().openConnection()
        connection.requestMethod = 'HEAD'
        connection.inputStream.text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Received unexpected request HEAD /a'
    }

    def "fails when some but not all expected parallel requests received"() {
        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        server.uri("a").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Failed to handle GET /a'
    }

    def "fails when request path does not match expected parallel request"() {
        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        server.uri("c").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Failed to handle GET /c'
    }

    def "fails when request method does not match expected parallel request"() {
        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        async {
            start {
                def connection = server.uri("a").toURL().openConnection()
                connection.requestMethod = 'HEAD'
                connection.inputStream.text
            }
            start {
                server.uri("b").toURL().text
            }
        }

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Received unexpected request HEAD /a'
    }

    def "succeeds when additional requests are made after parallel expectations are met"() {
        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("b").toURL().text }
        }
        server.uri("c").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.cause.message == 'Received unexpected request GET /c'
    }

}

