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
    def server = new BlockingHttpServer(1000)

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

    def "can wait for and release n concurrent requests"() {
        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("b").toURL().text }
            handle.waitForAllPendingCalls()
            handle.release(1)
            start { server.uri("c").toURL().text }
            handle.waitForAllPendingCalls()
            handle.release(1)
            handle.release(1)
            handle.waitForAllPendingCalls()
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can wait for and release a specific concurrent request"() {
        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                server.uri("b").toURL().text
            }
            start { server.uri("c").toURL().text }
            handle.waitForAllPendingCalls()
            handle.release("a")
            handle.waitForAllPendingCalls()
            handle.release("b")
            handle.waitForAllPendingCalls()
            handle.release("c")
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can wait for and release all concurrent request"() {
        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                server.uri("b").toURL().text
            }
            start { server.uri("c").toURL().text }
            handle.waitForAllPendingCalls()
            handle.release("a")
            handle.waitForAllPendingCalls()
            handle.releaseAll()
            handle.waitForAllPendingCalls()
            handle.releaseAll()
            handle.waitForAllPendingCalls()
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
        e.causes.message.sort() == ['Did not receive expected requests. Waiting for [b], received []']
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
        e.causes.message.sort() == ['Received unexpected request GET /a']
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
        e.causes.message.sort() == [
            'Failed to handle GET /b',
            "Unexpected request to 'b' received. Waiting for [a], already received []."
        ]
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
        e.causes.message.sort() == [
            'Did not receive expected requests. Waiting for [a], received []',
            'Received unexpected request HEAD /a'
        ]
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
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Timeout waiting for expected requests to be received. Still waiting for [b], received [a].'
        ]
    }

    def "fails when expected parallel request received after other request has failed"() {
        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        server.uri("a").toURL().text

        then:
        thrown(IOException)

        when:
        server.uri("b").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Failed to handle GET /b',
            'Timeout waiting for expected requests to be received. Still waiting for [b], received [a].'
        ]
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
        e.causes.message.sort() == [
            'Failed to handle GET /c',
            "Unexpected request to 'c' received. Waiting for [a, b], already received []."
        ]
    }

    def "fails when request method does not match expected parallel request"() {
        def failure1 = null
        def failure2 = null

        given:
        server.expectConcurrentExecution("a", "b")
        server.start()

        when:
        async {
            start {
                try {
                    def connection = server.uri("a").toURL().openConnection()
                    connection.requestMethod = 'HEAD'
                    connection.inputStream.text
                } catch (Throwable t) {
                    failure1 = t
                }
            }
            start {
                try {
                    server.uri("b").toURL().text
                } catch (Throwable t) {
                    failure2 = t
                }
            }
        }

        then:
        failure1 instanceof IOException
        failure2 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /b',
            'Received unexpected request HEAD /a',
            "Timeout waiting for expected requests to be received. Still waiting for [a], received [b]."
        ]
    }

    def "fails when additional requests are made after parallel expectations are met"() {
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
        e.causes.message.sort() == ['Received unexpected request GET /c']
    }

    def "fails when some but not all expected parallel requests received while waiting"() {
        def requestFailure = null

        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                try {
                    server.uri("a").toURL().text
                } catch (Throwable e) {
                    requestFailure = e
                }
            }
            handle.waitForAllPendingCalls()
        }

        then:
        def waitError = thrown(AssertionError)
        waitError.message == 'Timeout waiting for expected requests. Waiting for 1 further requests, received [a], released [], not yet received [b, c].'

        requestFailure instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            "Timeout waiting for expected requests. Waiting for 1 further requests, received [a], released [], not yet received [b, c]."
        ]
    }

    def "fails when expected parallel request received after waiting has failed"() {
        def requestFailure = null

        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                try {
                    server.uri("a").toURL().text
                } catch (Throwable e) {
                    requestFailure = e
                }
            }
            handle.waitForAllPendingCalls()
        }

        then:
        def waitError = thrown(AssertionError)
        waitError.message == 'Timeout waiting for expected requests. Waiting for 1 further requests, received [a], released [], not yet received [b, c].'

        requestFailure instanceof IOException

        when:
        server.uri("b").toURL().text

        then:
        thrown(IOException)

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Failed to handle GET /b',
            "Timeout waiting for expected requests. Waiting for 1 further requests, received [a], released [], not yet received [b, c]."
        ]
    }

    def "fails when unexpected request received while waiting"() {
        def failure1 = null
        def failure2 = null

        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                try {
                    server.uri("a").toURL().text
                } catch (Throwable t) {
                    failure1 = t
                }
            }
            start {
                server.waitForRequests(1)
                try {
                    server.uri("d").toURL().text
                } catch (Throwable t) {
                    failure2 = t
                }
            }
            handle.waitForAllPendingCalls()
        }

        then:
        def waitError = thrown(AssertionError)
        waitError.message == "Unexpected request to 'd' received. Waiting for 1 further requests, already received [a], released [], still expecting [b, c]."

        failure1 instanceof IOException
        failure2 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Failed to handle GET /d',
            "Unexpected request to 'd' received. Waiting for 1 further requests, already received [a], released [], still expecting [b, c]."
        ]
    }

    def "fails when too many concurrent requests received while waiting"() {
        def failure1 = null
        def failure2 = null
        def failure3 = null

        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                try {
                    server.uri("a").toURL().text
                } catch (Throwable t) {
                    failure1 = t
                }
            }
            start {
                server.waitForRequests(1)
                try {
                    server.uri("b").toURL().text
                } catch (Throwable t) {
                    failure2 = t
                }
            }
            start {
                server.waitForRequests(2)
                try {
                    server.uri("c").toURL().text
                } catch (Throwable t) {
                    failure3 = t
                }
            }
            server.waitForRequests(3)
            handle.waitForAllPendingCalls()
        }

        then:
        def waitError = thrown(AssertionError)
        waitError.message == "Unexpected request to 'c' received. Waiting for 0 further requests, already received [a, b], released [], still expecting [c]."

        failure1 instanceof IOException
        failure2 instanceof IOException
        failure3 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Failed to handle GET /b',
            'Failed to handle GET /c',
            "Unexpected request to 'c' received. Waiting for 0 further requests, already received [a, b], released [], still expecting [c]."
        ]
    }

    def "fails when request is not released"() {
        def failure1 = null
        def failure2 = null

        given:
        def handle = server.blockOnConcurrentExecutionAnyOf(2, "a", "b", "c")
        server.start()

        when:
        async {
            start {
                try {
                    server.uri("a").toURL().text
                } catch (Throwable t) {
                    failure1 = t
                }
            }
            start {
                server.waitForRequests(1)
                try {
                    server.uri("b").toURL().text
                } catch (Throwable t) {
                    failure2 = t
                }
            }
            handle.waitForAllPendingCalls()
        }

        then:
        failure1 instanceof IOException
        failure2 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            'Failed to handle GET /b',
            'Timeout waiting to be released. Waiting for 0 further requests, received [a, b], released [], not yet received [c].'
        ]
    }

}

