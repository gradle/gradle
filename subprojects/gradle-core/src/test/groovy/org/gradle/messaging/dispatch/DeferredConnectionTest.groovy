/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.dispatch


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.junit.Test
import org.gradle.util.MultithreadedTestCase

@RunWith(JMock.class)
public class DeferredConnectionTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Connection<Message> target = context.mock(Connection.class)
    private final Message message = new Message() {}
    private final EndOfStream endOfStream = new EndOfStream()
    private final DeferredConnection connection = new DeferredConnection()

    @Test
    public void dispatchBlocksUntilConnected() {
        context.checking {
            one(target).dispatch(message)
        }

        start {
            expectBlocksUntil(1) {
                connection.dispatch(message)
            }
        }
        run {
            syncAt(1)
            connection.connect(target)
        }

        waitForAll()
    }

    @Test
    public void receiveBlocksUntilConnected() {
        context.checking {
            one(target).receive()
            will(returnValue(message))
        }

        start {
            expectBlocksUntil(1) {
                assertThat(connection.receive(), sameInstance(message))
            }
        }
        run {
            syncAt(1)
            connection.connect(target)
        }

        waitForAll()
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedLocally() {
        connection.connect(target)
        
        context.checking {
            one(target).dispatch(endOfStream)
            one(target).receive()
            will {
                syncAt(1)
                return endOfStream
            }
            one(target).stop()
        }

        start {
            assertThat(connection.receive(), equalTo(endOfStream))
        }

        run {
            connection.dispatch(endOfStream)
            syncAt(1)
        }

        waitForAll()
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedRemotely() {
        connection.connect(target)

        context.checking {
            one(target).receive()
            will(returnValue(endOfStream))
            one(target).dispatch(endOfStream)
            one(target).stop()
        }

        start {
            assertThat(connection.receive(), equalTo(endOfStream))
            syncAt(1)
            assertThat(connection.receive(), nullValue())
        }

        run {
            syncAt(1)
            connection.dispatch(endOfStream)
        }

        waitForAll()
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedLocallyWhenNotConnected() {
        start {
            assertThat(connection.receive(), equalTo(endOfStream))
            assertThat(connection.receive(), nullValue())
        }

        run {
            connection.dispatch(endOfStream)
        }

        waitForAll()
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedByConnectionClose() {
        connection.connect(target)

        context.checking {
            one(target).receive()
            will(returnValue(null))
            one(target).stop()
        }

        assertThat(connection.receive(), equalTo(endOfStream))
        assertThat(connection.receive(), nullValue())
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedByDispatchFailure() {
        connection.connect(target)

        context.checking {
            one(target).dispatch(message)
            will(throwException(new RuntimeException()))
        }

        connection.dispatch(message)
        assertThat(connection.receive(), equalTo(endOfStream))
        assertThat(connection.receive(), nullValue())
    }

    @Test
    public void performsEndOfStreamNegotiationInitiatedByReceiveFailure() {
        connection.connect(target)

        context.checking {
            one(target).receive()
            will(throwException(new RuntimeException()))
        }

        assertThat(connection.receive(), equalTo(endOfStream))
        assertThat(connection.receive(), nullValue())
    }
}
