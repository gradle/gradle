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





package org.gradle.messaging.remote.internal

import org.gradle.messaging.dispatch.Dispatch
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock.class)
public class DefaultMultiChannelConnectionTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Connection<Message> target = context.mock(Connection.class)
    private final TestMessage message = new TestMessage()
    private DefaultMultiChannelConnection connection

    @Test
    public void dispatchesOutgoingMessageToTargetConnection() {
        clockTick(1).hasParticipants(2)
        context.checking {
            one(target).receive()
            will {
                syncAt(1)
                return null
            }
            one(target).dispatch(new ChannelMetaInfo('channel1', 0))
            one(target).dispatch(new ChannelMessage(0, message))
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))
        run {
            connection.addOutgoingChannel('channel1').dispatch(message)
            syncAt(1)
        }

        connection.stop()
    }

    @Test
    public void dispatchesIncomingMessageToHandler() {
        clockTick(1).hasParticipants(2)
        Dispatch<Message> handler = context.mock(Dispatch.class)
        context.checking {
            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel1', 0)))
            one(target).receive()
            will(returnValue(new ChannelMessage(0, message)))
            one(handler).dispatch(message)
            one(target).receive()
            will {
                syncAt(1)
                return null
            }
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            connection.addIncomingChannel('channel1', handler)

            syncAt(1)
        }

        connection.stop()
    }

    @Test
    public void stuckHandlerDoesNotBlockOtherHandlers() {
        Dispatch<Message> handler1 = context.mock(Dispatch.class, 'handler1')
        Dispatch<Message> handler2 = context.mock(Dispatch.class, 'handler2')
        TestMessage message2 = new TestMessage()
        clockTick(1).hasParticipants(3)
        clockTick(2).hasParticipants(3)

        context.checking {
            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel1', 0)))
            one(target).receive()
            will(returnValue(new ChannelMessage(0, message)))
            one(handler1).dispatch(message)
            will {
                syncAt(1)
                syncAt(2)
            }

            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel2', 1)))
            one(target).receive()
            will {
                syncAt(1)
                return new ChannelMessage(1, message2)
            }
            one(handler2).dispatch(message2)
            will {
                shouldBeAt(1)
                syncAt(2)
            }

            one(target).receive()
            will {
                return null
            }
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            connection.addIncomingChannel('channel1', handler1)
            connection.addIncomingChannel('channel2', handler2)

            syncAt(1)
            syncAt(2)
        }

        connection.stop()
    }

    @Test
    public void discardsMessageWhenHandlerIsBroken() {
        clockTick(1).hasParticipants(2)
        Dispatch<Message> handler = context.mock(Dispatch.class)
        context.checking {
            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel1', 1)))
            one(target).receive()
            will(returnValue(new ChannelMessage(1, message)))
            one(handler).dispatch(message)
            will(throwException(new RuntimeException()))
            one(target).receive()
            will {
                syncAt(1)
                return null
            }
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            connection.addIncomingChannel('channel1', handler)

            syncAt(1)
        }

        connection.stop()
    }

    @Test
    public void queuesIncomingChannelMessageUntilHandlerIsAvailable() {
        Dispatch<Message> handler = context.mock(Dispatch.class)
        TestMessage message2 = new TestMessage()

        clockTick(1).hasParticipants(2)

        context.checking {
            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel1', 1)))
            one(target).receive()
            will(returnValue(new ChannelMessage(1, message)))
            one(target).receive()
            will {
                syncAt(1)
                return new ChannelMessage(1, message2)
            }
            one(handler).dispatch(message)
            one(handler).dispatch(message2)
            one(target).receive()
            will {
                return null
            }
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            syncAt(1)
            connection.addIncomingChannel('channel1', handler)
        }

        connection.stop()
    }

    @Test
    public void stopBlocksUntilAllIncomingMessagesAreHandled() {
        Dispatch<Message> handler = context.mock(Dispatch.class)
        clockTick(1).hasParticipants(2)

        context.checking {
            one(target).receive()
            will(returnValue(new ChannelMetaInfo('channel1', 1)))
            one(target).receive()
            will(returnValue(new ChannelMessage(1, message)))
            one(handler).dispatch(message)
            will {
                syncAt(1)
            }
            one(target).receive()
            will(returnValue(null))
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            connection.addIncomingChannel('channel1', handler)

            expectBlocksUntil(1) {
                connection.stop()
            }
        }
    }

    @Test
    public void stopBlocksUntilAllOutgoingMessagesAreDispatched() {
        clockTick(1).hasParticipants(3)

        context.checking {
            one(target).receive()
            will {
                syncAt(1)
                return null
            }

            one(target).dispatch(new ChannelMetaInfo('channel1', 0))
            one(target).dispatch(new ChannelMessage(0, message))
            will {
                syncAt(1)
            }
            one(target).dispatch(new EndOfStreamEvent())
            one(target).stop()
        }

        connection = new DefaultMultiChannelConnection(executorFactory, 'connection', target, new URI('test:local'), new URI('test:remote'))

        run {
            connection.addOutgoingChannel('channel1').dispatch(message)

            expectBlocksUntil(1) {
                connection.stop()
            }
        }
    }
}

class TestMessage extends Message {
}