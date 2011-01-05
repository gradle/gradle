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

import static org.hamcrest.Matchers.*

import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.Sequence
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*

@RunWith(JMock.class)
public class AsyncReceiveTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Dispatch<String> target1 = context.mock(Dispatch.class, "target1")
    private final Dispatch<String> target2 = context.mock(Dispatch.class, "target2")
    private final Receive<String> source1 = context.mock(Receive.class, "source1")
    private final AsyncReceive<String> dispatch = new AsyncReceive<String>(executor, target1)

    @Test
    public void dispatchesReceivedMessagesToTargetUntilEndOfStreamReached() {
        clockTick(1).hasParticipants(2)

        context.checking {
            Sequence receive = context.sequence('receive')
            Sequence dispatch = context.sequence('dispatch')

            one(source1).receive()
            will(returnValue('message1'))
            inSequence(receive)

            one(source1).receive()
            will(returnValue('message2'))
            inSequence(receive)

            one(source1).receive()
            inSequence(receive)
            will {
                syncAt(1)
                return null
            }

            one(target1).dispatch('message1')
            inSequence(dispatch)

            one(target1).dispatch('message2')
            inSequence(dispatch)
        }

        dispatch.receiveFrom(source1)

        run {
            syncAt(1)
        }
        
        dispatch.stop()
    }

    @Test
    public void stopBlocksUntilAllReceiveCallsHaveReturned() {
        context.checking {
            one(source1).receive()
            will {
                syncAt(1)
                return 'message'
            }
            one(target1).dispatch('message')
            will {
                syncAt(2)
            }
        }

        run {
            dispatch.receiveFrom(source1)
            syncAt(1)
            expectBlocksUntil(2) {
                dispatch.stop()
            }
        }
    }

    @Test
    public void requestStopDoesNotBlock() {
        context.checking {
            one(source1).receive()
            will {
                syncAt(1)
                syncAt(2)
                return 'message'
            }
            one(target1).dispatch('message')
        }

        run {
            dispatch.receiveFrom(source1)
            syncAt(1)
            dispatch.requestStop()
            syncAt(2)
        }

        dispatch.stop()
    }
}
