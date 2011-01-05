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
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock.class)
public class EndOfStreamFilterTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery() {{ imposteriser = ClassImposteriser.INSTANCE }}
    private final Dispatch<Message> target = context.mock(Dispatch.class)
    private final Runnable action = context.mock(Runnable.class)
    private final EndOfStreamFilter filter = new EndOfStreamFilter(target, action)
    
    @Test
    public void stopBlocksUntilEndOfStreamReceived() {
        context.checking {
            one(action).run()
        }

        start {
            expectBlocksUntil(1) {
                filter.stop()
            }
        }

        run {
            syncAt(1)
            filter.dispatch(new EndOfStreamEvent())
        }
    }

    @Test
    public void canStopFromMultipleThreads() {
        context.checking {
            one(action).run()
        }

        filter.dispatch(new EndOfStreamEvent())

        start {
            filter.stop()
        }
        run {
            filter.stop()
        }
    }
}
