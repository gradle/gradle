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
package org.gradle.listener

import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.Sequence
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock.class)
public class AsyncProxyTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestObject target = context.mock(TestObject.class)

    @Test
    public void proxyObjectDelegatesMethodCallsToTargetObject() {
        context.checking {
            Sequence sequence = context.sequence('sequence')
            one(target).doStuff('one')
            inSequence(sequence)
            one(target).doStuff('two')
            inSequence(sequence)
        }

        AsyncProxy proxy = new AsyncProxy(TestObject.class, target, executor)
        proxy.source.doStuff('one')
        proxy.source.doStuff('two')

        proxy.stop()
    }

    @Test
    public void blockingTargetObjectDoesNotBlockProxyObject() {
        context.checking {
            one(target).doStuff('one')
            will {
                syncAt(1)
                syncAt(2)
            }
            one(target).doStuff('two')
            one(target).doStuff('three')
        }

        AsyncProxy proxy = new AsyncProxy(TestObject.class, target, executor)

        run {
            proxy.source.doStuff('one')
            syncAt(1)
            proxy.source.doStuff('two')
            proxy.source.doStuff('three')
            syncAt(2)
        }

        proxy.stop()
    }

    @Test
    public void queuesMethodCallsUntilProxyStarted() {
        AsyncProxy proxy = new AsyncProxy(TestObject.class, null, executor)

        run {
            proxy.source.doStuff('one')
            proxy.source.doStuff('two')
        }

        context.checking {
            one(target).doStuff('one')
            one(target).doStuff('two')
        }

        proxy.start(target)
        proxy.stop()
    }

    @Test
    public void stopBlocksUntilAllMethodCallsInvoked() {
        context.checking {
            one(target).doStuff('one')
            will {
                syncAt(1)
                syncAt(2)
            }
        }

        AsyncProxy proxy = new AsyncProxy(TestObject.class, target, executor)
        proxy.source.doStuff('one')

        run {
            syncAt(1)
            expectBlocksUntil(2) {
                proxy.stop()
            }
        }
    }
}

interface TestObject {
    void doStuff(String param)
}
