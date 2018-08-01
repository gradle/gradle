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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class BlockingHttpServerTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def server = new BlockingHttpServer(1000)

    def "serves HTTP resources"() {
        server.expect("a")
        server.expect("b")
        server.start()

        when:
        def a = server.uri("a")
        def b = server.uri("b")

        then:
        a.scheme == "http"
        b.scheme == "http"

        when:
        a.toURL().text
        b.toURL().text
        server.stop()

        then:
        noExceptionThrown()
    }

    def "succeeds when expected serial requests are made"() {
        given:
        server.expect("a")
        server.expect("b")
        server.expect("c")
        server.start()

        when:
        server.uri("a").toURL().text
        server.uri("b").toURL().text
        server.uri("c").toURL().text
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can specify the content to return in response to a GET request"() {
        def file = tmpDir.createFile("thing.txt")
        file.text = "123"

        given:
        server.expect(server.resource("a"))
        server.expect(server.file("b", file))
        server.expect(server.resource("c", "this is the content"))
        server.start()

        when:
        server.uri("a").toURL().text == ""
        server.uri("b").toURL().text == "123"
        server.uri("c").toURL().text == "this is the content"
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can specify to return 404 response to a GET request"() {
        given:
        server.expect(server.missing("a"))
        server.start()

        when:
        server.uri("a").toURL().text

        then:
        thrown(FileNotFoundException)

        when:
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can specify an action to run to generate response for a GET request"() {
        given:
        server.expect(server.get("a", { e ->
            e.sendResponseHeaders(200, 0)
        }))
        server.expect(server.get("b", { e ->
            def str = "this is the content"
            e.sendResponseHeaders(200, str.bytes.length)
            e.responseBody.write(str.bytes)
        }))
        server.start()

        when:
        server.uri("a").toURL().text == ""
        server.uri("b").toURL().text == "this is the content"
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can expect a PUT request"() {
        given:
        server.expect(server.put("a"))
        server.start()

        when:
        def connection = server.uri("a").toURL().openConnection()
        connection.requestMethod = 'PUT'
        connection.doOutput = true
        connection.outputStream << "123".bytes
        connection.inputStream.text

        server.stop()

        then:
        noExceptionThrown()
    }

    def "can expect a POST request and provide action to generate response"() {
        given:
        server.expect(server.post("a", { e ->
            def str = "this is the content"
            e.sendResponseHeaders(200, str.bytes.length)
            e.responseBody.write(str.bytes)
        }))
        server.start()

        when:
        def connection = server.uri("a").toURL().openConnection()
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.outputStream << "123".bytes
        def result = connection.inputStream.text

        server.stop()

        then:
        result == "this is the content"
        noExceptionThrown()
    }

    def "can send partial response and block"() {
        given:
        def request1 = server.sendSomeAndBlock("a", new byte[2048])
        def request2 = server.sendSomeAndBlock("b", new byte[2048])
        server.expect(request1)
        server.expect(request2)
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                instant.aDone
                server.uri("b").toURL().text
            }
            request1.waitUntilBlocked()
            instant.aBlocked
            request1.release()
            request2.waitUntilBlocked()
            request2.release()
        }
        server.stop()

        then:
        instant.aDone > instant.aBlocked
    }

    def "succeeds when expected concurrent requests are made"() {
        given:
        server.expectConcurrent("a", "b", "c")
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

    def "can wait for multiple concurrent requests to same URL"() {
        given:
        server.expectConcurrent("a", "a", "a")
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("a").toURL().text }
            start { server.uri("a").toURL().text }
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can wait for multiple concurrent requests to same URL using request objects"() {
        given:
        def expected = server.resource("a")
        server.expectConcurrent(expected, expected, expected)
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("a").toURL().text }
            start { server.uri("a").toURL().text }
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "can send partial response and block for concurrent requests"() {
        given:
        def request1 = server.sendSomeAndBlock("a", new byte[2048])
        def request2 = server.sendSomeAndBlock("b", new byte[2048])
        server.expectConcurrent(request1, request2)
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                instant.aDone
            }
            start {
                server.uri("b").toURL().text
                instant.bDone
            }
            request1.waitUntilBlocked()
            instant.aBlocked
            request1.release()
            request2.waitUntilBlocked()
            instant.bBlocked
            request2.release()
        }
        server.stop()

        then:
        instant.aDone > instant.aBlocked
        instant.bDone > instant.bBlocked
    }

    def "can wait for and release n concurrent requests"() {
        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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

    def "can wait for and release n concurrent requests to same URL"() {
        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "a", "a")
        server.start()

        when:
        async {
            start { server.uri("a").toURL().text }
            start { server.uri("a").toURL().text }
            handle.waitForAllPendingCalls()
            handle.release(1)
            start { server.uri("a").toURL().text }
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
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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

    def "can wait for and release all concurrent requests"() {
        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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

    def "can wait for and release all concurrent requests to same URL"() {
        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "a", "b", "b")
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                server.uri("b").toURL().text
            }
            start {
                server.uri("a").toURL().text
                server.uri("b").toURL().text
            }
            handle.waitForAllPendingCalls()
            handle.release("a")
            handle.release("a")
            handle.waitForAllPendingCalls()
            handle.release(1)
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

    def "can send partial response and block again after blocking for concurrent requests"() {
        given:
        def request1 = server.sendSomeAndBlock("a", new byte[2048])
        def request2 = server.sendSomeAndBlock("b", new byte[2048])
        def handle = server.expectConcurrentAndBlock(2, request1, request2)
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
                instant.aDone
            }
            start {
                server.uri("b").toURL().text
                instant.bDone
            }
            handle.waitForAllPendingCalls()
            handle.releaseAll()
            request1.waitUntilBlocked()
            instant.aBlocked
            request1.release()
            request2.waitUntilBlocked()
            instant.bBlocked
            request2.release()
        }
        server.stop()

        then:
        instant.aDone > instant.aBlocked
        instant.bDone > instant.bBlocked
    }

    def "can chain expectations"() {
        given:
        server.expectConcurrent("a", "b")
        server.expect("c")
        def handle = server.expectConcurrentAndBlock(2, "d", "e")
        server.expect("f")
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
            }
            start {
                server.uri("b").toURL().text
            }
            server.waitForRequests(2)
            server.uri("c").toURL().text
            start {
                server.uri("d").toURL().text
            }
            start {
                server.uri("e").toURL().text
            }
            handle.waitForAllPendingCalls()
            handle.releaseAll()
            server.uri("f").toURL().text
        }
        server.stop()

        then:
        noExceptionThrown()
    }

    def "fails on stop when expected serial request is not made"() {
        given:
        server.expect("a")
        server.expect("b")
        server.start()

        when:
        server.uri("a").toURL().text
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == ['Did not receive expected requests. Waiting for [GET b], received []']
    }

    def "fails when request is received after serial expectations met"() {
        given:
        server.expect("a")
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
        server.expect("a")
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
            "Unexpected request GET b received. Waiting for [GET a], already received []."
        ]
    }

    def "fails when request method does not match expected serial GET request"() {
        given:
        server.expect("a")
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
            'Failed to handle HEAD /a',
            'Unexpected request HEAD a received. Waiting for [GET a], already received [].'
        ]
    }

    def "fails when request method does not match expected serial PUT request"() {
        given:
        server.expect(server.put("a"))
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
            'Unexpected request GET a received. Waiting for [PUT a], already received [].'
        ]
    }

    def "fails when request method does not match expected serial POST request"() {
        given:
        server.expect(server.post("a", { e -> e.sendResponseHeaders(200, 0) }))
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
            'Unexpected request GET a received. Waiting for [POST a], already received [].'
        ]
    }

    def "fails when some but not all expected parallel requests received"() {
        given:
        server.expectConcurrent("a", "b")
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
            'Timeout waiting for expected requests to be received. Still waiting for [GET b], received [GET a].'
        ]
    }

    def "fails when expected parallel request received after other request has failed"() {
        given:
        server.expectConcurrent("a", "b")
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
            'Timeout waiting for expected requests to be received. Still waiting for [GET b], received [GET a].'
        ]
    }

    def "fails when request path does not match expected parallel request"() {
        given:
        server.expectConcurrent("a", "b")
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
            "Unexpected request GET c received. Waiting for [GET a, GET b], already received []."
        ]
    }

    def "fails when request method does not match expected parallel request"() {
        def failure1 = null
        def failure2 = null
        def failure3 = null

        given:
        server.expectConcurrent(server.resource("a"), server.resource("b"), server.put("c"))
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
        }

        then:
        failure1 instanceof IOException
        failure2 instanceof IOException
        failure3 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /b',
            'Failed to handle GET /c',
            'Failed to handle HEAD /a',
            "Unexpected request HEAD a received. Waiting for [GET a, GET b, PUT c], already received []."
        ]
    }

    def "fails when request method does not match expected blocking parallel request"() {
        def failure1 = null
        def failure2 = null
        def failure3 = null

        given:
        def handler = server.expectConcurrentAndBlock(server.resource("a"), server.resource("b"), server.put("c"))
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
        }

        then:
        failure1 instanceof IOException
        failure2 instanceof IOException
        failure3 instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /b',
            'Failed to handle GET /c',
            'Failed to handle HEAD /a',
            "Unexpected request HEAD a received. Waiting for 3 further requests, already received [], released [], still expecting [GET a, GET b, PUT c]."
        ]
    }

    def "fails when additional requests are made after parallel expectations are met"() {
        given:
        server.expectConcurrent("a", "b")
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
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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
        waitError.message == 'Timeout waiting for expected requests. Waiting for 1 further requests, received [GET a], released [], not yet received [GET b, GET c].'

        requestFailure instanceof IOException

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Failed to handle GET /a',
            "Timeout waiting for expected requests. Waiting for 1 further requests, received [GET a], released [], not yet received [GET b, GET c]."
        ]
    }

    def "fails when expected parallel request received after waiting has failed"() {
        def requestFailure = null

        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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
        waitError.message == 'Timeout waiting for expected requests. Waiting for 1 further requests, received [GET a], released [], not yet received [GET b, GET c].'

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
            "Timeout waiting for expected requests. Waiting for 1 further requests, received [GET a], released [], not yet received [GET b, GET c]."
        ]
    }

    def "fails when unexpected request received while waiting"() {
        def failure1 = null
        def failure2 = null

        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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
        waitError.message == "Unexpected request GET d received. Waiting for 1 further requests, already received [GET a], released [], still expecting [GET b, GET c]."

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
            "Unexpected request GET d received. Waiting for 1 further requests, already received [GET a], released [], still expecting [GET b, GET c]."
        ]
    }

    def "fails when too many concurrent requests received while waiting"() {
        def failure1 = null
        def failure2 = null
        def failure3 = null

        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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
        waitError.message == "Unexpected request GET c received. Waiting for 0 further requests, already received [GET a, GET b], released [], still expecting [GET c]."

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
            "Unexpected request GET c received. Waiting for 0 further requests, already received [GET a, GET b], released [], still expecting [GET c]."
        ]
    }

    def "fails when attempting to wait before server is started"() {
        given:
        def handle1 = server.expectConcurrentAndBlock("a", "b")
        def handle2 = server.expectConcurrentAndBlock(2, "c", "d")
        def request1 = server.sendSomeAndBlock("e", new byte[2048])
        server.expect(request1)

        when:
        handle1.waitForAllPendingCalls()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot wait as the server is not running."

        when:
        handle2.waitForAllPendingCalls()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot wait as the server is not running."

        when:
        request1.waitUntilBlocked()

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == "Cannot wait as the server is not running."

        when:
        server.stop()

        then:
        def e4 = thrown(RuntimeException)
        e4.message == 'Failed to handle all HTTP requests.'
        e4.causes.message.sort() == [
            'Did not handle all expected requests. Waiting for 2 further requests, received [], released [], not yet received [GET a, GET b].',
            'Did not handle all expected requests. Waiting for 2 further requests, received [], released [], not yet received [GET c, GET d].',
            'Did not receive expected requests. Waiting for [GET e], received []'
        ]
    }

    def "fails when attempting to wait before previous requests released"() {
        given:
        server.expectConcurrentAndBlock(2, "a", "b")
        def handle2 = server.expectConcurrentAndBlock(2, "c", "d")
        server.start()

        when:
        handle2.waitForAllPendingCalls()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot wait as no requests have been released. Waiting for [GET a, GET b], received []."

        when:
        server.stop()

        then:
        def e2 = thrown(RuntimeException)
        e2.message == 'Failed to handle all HTTP requests.'
        e2.causes.message.sort() == [
            'Did not handle all expected requests. Waiting for 2 further requests, received [], released [], not yet received [GET a, GET b].',
            'Did not handle all expected requests. Waiting for 2 further requests, received [], released [], not yet received [GET c, GET d].',
        ]
    }

    def "fails when request is not released"() {
        def failure1 = null
        def failure2 = null

        given:
        def handle = server.expectConcurrentAndBlock(2, "a", "b", "c")
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
            // Should release the requests here
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
            'Timeout waiting to be released. Waiting for 0 further requests, received [GET a, GET b], released [], not yet received [GET c].'
        ]
    }

    def "fails when request is not released after sending partial response"() {
        given:
        def request = server.sendSomeAndBlock("a", new byte[2048])
        def handle = server.expectConcurrentAndBlock(1, request)
        server.start()

        when:
        async {
            start {
                server.uri("a").toURL().text
            }
            start {
                handle.waitForAllPendingCalls()
                handle.releaseAll()
                // Should release the request here
            }
        }

        then:
        // TODO - reading from the URL should fail
        noExceptionThrown()

        when:
        server.stop()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Failed to handle all HTTP requests.'
        e.causes.message.sort() == [
            'Timeout waiting to be released after sending some content.'
        ]
    }

    def "fails when attempting to wait for a request that has not been released to send partial response"() {
        given:
        server.expect("a")
        def request1 = server.sendSomeAndBlock("b", new byte[2048])
        def request2 = server.sendSomeAndBlock("c", new byte[2048])
        server.expectConcurrentAndBlock(1, request1)
        server.expect(request2)
        server.start()

        when:
        request1.waitUntilBlocked()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot wait as the request to 'b' has not been released yet."

        when:
        request2.waitUntilBlocked()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot wait as no requests have been released. Waiting for [GET b], received []."

        when:
        server.stop()

        then:
        def e3 = thrown(RuntimeException)
        e3.message == 'Failed to handle all HTTP requests.'
        e3.causes.message.sort() == [
            'Did not handle all expected requests. Waiting for 1 further requests, received [], released [], not yet received [GET b].',
            'Did not receive expected requests. Waiting for [GET a], received []',
            'Did not receive expected requests. Waiting for [GET c], received []'
        ]
    }

}

