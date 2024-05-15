/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.daemon.clientinput


import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class StdInStreamTest extends ConcurrentSpec {
    def "read byte requests input from client and blocks until it is available"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)
        def text = "some text"
        def bytes = text.bytes

        when:
        async {
            start {
                def b1 = stream.read()
                assert b1 == bytes[0]
                def b2 = stream.read()
                assert b2 == bytes[1]
            }
            thread.blockUntil.requested
            thread.block()
            stream.received(bytes)
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested }
    }

    def "read byte returns when stream is closed"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)

        when:
        async {
            start {
                def b1 = stream.read()
                assert b1 == -1
            }
            thread.blockUntil.requested
            stream.close()
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested }
    }

    def "read bytes requests input from client and blocks until it is available"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)
        def text = "some text"
        def bytes = text.bytes

        when:
        async {
            start {
                def buffer = new byte[1024]
                def nread = stream.read(buffer)
                assert nread == bytes.length
                assert new String(buffer, 0, nread) == text
            }
            thread.blockUntil.requested
            thread.block()
            stream.received(bytes)
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested }
    }

    def "can read multiple batches of input from client"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)
        def text1 = "some text"
        def bytes1 = text1.bytes
        def text2 = "more"
        def bytes2 = text2.bytes

        when:
        async {
            start {
                def buffer = new byte[1024]

                def nread = stream.read(buffer)
                assert nread == bytes1.length
                assert new String(buffer, 0, nread) == text1

                nread = stream.read(buffer)
                assert nread == bytes2.length
                assert new String(buffer, 0, nread) == text2
            }
            thread.blockUntil.requested1
            stream.received(bytes1)
            thread.blockUntil.requested2
            stream.received(bytes2)
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested1 }
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested2 }
    }

    def "read bytes returns when stream is closed"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)

        when:
        async {
            start {
                def buffer = new byte[1024]
                def nread = stream.read(buffer)
                assert nread == -1
            }
            thread.blockUntil.requested
            stream.close()
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested }
    }

    def "read bytes continues to return buffered content when stream is closed"() {
        def dispatch = Mock(OutputEventListener)
        def stream = new StdInStream(dispatch)
        def text = "some text"
        def bytes = text.bytes

        when:
        async {
            start {
                def buffer = new byte[5]
                def nread = stream.read(buffer)
                assert nread == 5
                assert new String(buffer, 0, nread) == "some "

                nread = stream.read(buffer)
                assert nread == 4
                assert new String(buffer, 0, nread) == "text"

                nread = stream.read(buffer)
                assert nread == -1
            }
            thread.blockUntil.requested
            stream.received(bytes)
            stream.close()
        }

        then:
        1 * dispatch.onOutput({ it instanceof ReadStdInEvent }) >> { instant.requested }
    }

}
