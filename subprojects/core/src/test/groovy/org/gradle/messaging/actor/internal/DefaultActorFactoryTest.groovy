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

package org.gradle.messaging.actor.internal

import org.gradle.messaging.actor.Actor
import org.gradle.messaging.dispatch.DispatchException
import org.gradle.messaging.dispatch.MethodInvocation
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

@RunWith(JMock.class)
class DefaultActorFactoryTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TargetObject target = context.mock(TargetObject.class)
    private final DefaultActorFactory factory = new DefaultActorFactory(executorFactory)

    @After
    public void tearDown() {
        factory.stop()
    }

    @Test
    public void createsAnActorForATargetObject() {
        factory.createActor(target) != null
    }

    @Test
    public void cachesTheActorForATargetObject() {
        Actor actor1 = factory.createActor(target)
        Actor actor2 = factory.createActor(target)
        assertThat(actor2, sameInstance(actor1))
    }

    @Test
    public void returnsTargetObjectIfTargetObjectIsAnActor() {
        Actor actor1 = factory.createActor(target)
        Actor actor2 = factory.createActor(actor1)
        assertThat(actor2, sameInstance(actor1))
    }

    @Test
    public void actorDispatchesMethodInvocationToTargetObject() {
        Actor actor = factory.createActor(target)

        context.checking {
            one(target).doStuff('param')
            will {
                syncAt(1)
            }
        }

        run {
            actor.dispatch(new MethodInvocation(TargetObject.class.getMethod('doStuff', String.class), ['param'] as Object[]))
            syncAt(1)
        }
    }

    @Test
    public void actorProxyDispatchesMethodCallToTargetObject() {
        Actor actor = factory.createActor(target)
        TargetObject proxy = actor.getProxy(TargetObject.class)

        context.checking {
            one(target).doStuff('param')
            will {
                syncAt(1)
            }
        }

        run {
            proxy.doStuff('param')
            syncAt(1)
        }
    }

    @Test
    public void actorStopPropagatesMethodFailure() {
        Actor actor = factory.createActor(target)
        TargetObject proxy = actor.getProxy(TargetObject.class)
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(target).doStuff('param')
            will {
                throw failure
            }
        }

        run {
            proxy.doStuff('param')
            try {
                actor.stop()
                fail()
            } catch (DispatchException e) {
                assertThat(e.message, startsWith('Failed to dispatch message'))
                assertThat(e.cause, sameInstance(failure))
            }
        }
    }

    @Test
    public void actorStopBlocksUntilAllMethodCallsComplete() {
        Actor actor = factory.createActor(target)
        TargetObject proxy = actor.getProxy(TargetObject.class)

        context.checking {
            one(target).doStuff('param')
            will {
                syncAt(1)
            }
        }

        run {
            proxy.doStuff('param')
            expectBlocksUntil(1) {
                actor.stop()
            }
        }
    }

    @Test
    public void factoryStopBlocksUntilAllMethodCallsComplete() {
        Actor actor = factory.createActor(target)
        TargetObject proxy = actor.getProxy(TargetObject.class)

        context.checking {
            one(target).doStuff('param')
            will {
                syncAt(1)
            }
        }

        run {
            proxy.doStuff('param')
            expectBlocksUntil(1) {
                factory.stop()
            }
        }
    }
}

interface TargetObject {
    void doStuff(String param)

    void stopDoingStuff()
}
