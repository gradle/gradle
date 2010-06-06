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

import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
public class AsyncDispatchTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Dispatch<String> target1 = context.mock(Dispatch.class, "target1")
    private final Dispatch<String> target2 = context.mock(Dispatch.class, "target2")
    private final AsyncDispatch<String> dispatch = new AsyncDispatch<String>(executor)

    @Test
    public void dispatchesMessageToAnIdleTarget() {
        context.checking {
            one(target1).dispatch('message1')
            one(target1).dispatch('message2')
        }

        dispatch.dispatchTo(target1)

        dispatch.dispatch('message1')
        dispatch.dispatch('message2')

        dispatch.stop()
    }

    @Test
    public void dispatchDoesNotBlockWhileNoIdleTargetAvailable() {
        context.checking {
            one(target1).dispatch('message1')
            will {
                syncAt(1)
                syncAt(2)
            }
            one(target2).dispatch('message2')
            will {
                syncAt(2)
                syncAt(3)
            }
            one(target1).dispatch('message3')
            will {
                syncAt(3)
            }
        }

        run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            syncAt(1)

            dispatch.dispatchTo(target2)
            dispatch.dispatch('message2')
            syncAt(2)

            dispatch.dispatch('message3')
            syncAt(3)
        }

        dispatch.stop()
    }

    @Test
    public void canStopFromMultipleThreads() {
        dispatch.dispatchTo(target1)

        start {
            dispatch.stop()
        }
        start {
            dispatch.stop()
        }
    }

    @Test
    public void canRequestStopFromMultipleThreads() {
        dispatch.dispatchTo(target1)

        start {
            dispatch.requestStop()
        }
        start {
            dispatch.requestStop()
        }

        waitForAll()
        dispatch.stop()
    }

    @Test
    public void stopBlocksUntilAllMessagesDispatched() {
        context.checking {
            one(target1).dispatch('message1')
            will {
                syncAt(1)
                syncAt(2)
                syncAt(3)
            }
        }

        context.checking {
            one(target2).dispatch('message2')
            will {
                syncAt(2)
                syncAt(3)
            }
        }

        run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            syncAt(1)

            dispatch.dispatchTo(target2)
            dispatch.dispatch('message2')
            syncAt(2)

            expectBlocksUntil(3) {
                dispatch.stop()
            }
        }
    }

    @Test
    public void requestStopDoesNotBlockWhenMessagesAreQueued() {
        context.checking {
            one(target1).dispatch('message1')
            will {
                syncAt(1)
                syncAt(2)
            }
        }

        run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            syncAt(1)
            dispatch.requestStop()
            shouldBeAt(1)
            syncAt(2)
        }

        waitForAll()
        dispatch.stop()
    }

    @Test
    public void stopFailsWhenNoTargetsAvailableToDeliverQueuedMessages() {
        dispatch.dispatch('message1')
        try {
            dispatch.stop()
            fail()
        } catch (IllegalStateException e) {
            assertThat(e.message, equalTo('Cannot wait for messages to be dispatched, as there are no dispatch threads running.'))
        }
    }

    @Test
    public void stopFailsWhenAllTargetsHaveFailed() {
        context.checking {
            one(target1).dispatch('message1')
            will {
                RuntimeException failure = new RuntimeException()
                willFailWith(sameInstance(failure))
                throw failure
            }
        }
        dispatch.dispatchTo(target1)
        dispatch.dispatch('message1')
        dispatch.dispatch('message2')

        try {
            dispatch.stop()
            fail()
        } catch (IllegalStateException e) {
            assertThat(e.message, equalTo('Cannot wait for messages to be dispatched, as there are no dispatch threads running.'))
        }
    }
    
    @Test
    public void cannotDispatchMessagesAfterStop() {
        dispatch.stop()
        try {
            dispatch.dispatch('message')
            fail()
        } catch (IllegalStateException e) {
            assertThat(e.message, equalTo('This message dispatch has been stopped.'))
        }
    }
}
