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
import static org.junit.Assert.*

@RunWith(JMock.class)
public class EndOfStreamDispatchTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Dispatch<Message> target = context.mock(Dispatch.class)
    private final EndOfStreamDispatch dispatch = new EndOfStreamDispatch(target)

    @Test
    public void writesEndOfStreamMessageOnStop() {
        context.checking {
            one(target).dispatch(new EndOfStreamEvent())
        }

        dispatch.stop()
    }

    @Test
    public void stopBlocksUntilCurrentlyPendingMessageDelivered() {
        Message message = new Message() {}

        context.checking {
            one(target).dispatch(message)
            will {
                syncAt(1)
                syncAt(2)
            }
            one(target).dispatch(new EndOfStreamEvent())
        }

        start {
            dispatch.dispatch(message)
        }

        run {
            syncAt(1)
            expectBlocksUntil(2) {
                dispatch.stop()
            }
        }
    }

    @Test
    public void cannotDispatchAfterStop() {
        context.checking {
            one(target).dispatch(new EndOfStreamEvent())
        }

        dispatch.stop()

        try {
            dispatch.dispatch(new Message(){})
            fail()
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void canStopFromMultipleThreads() {
        context.checking {
            one(target).dispatch(new EndOfStreamEvent())
        }

        start {
            dispatch.stop()
        }
        start {
            dispatch.stop()
        }

        waitForAll()
    }
}
