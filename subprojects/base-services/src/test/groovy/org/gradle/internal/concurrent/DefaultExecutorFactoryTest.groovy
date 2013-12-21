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
package org.gradle.internal.concurrent

import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(JMock)
class DefaultExecutorFactoryTest extends MultithreadedTestCase {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final DefaultExecutorFactory factory = new DefaultExecutorFactory() {
        def ExecutorService createExecutor(String displayName) {
            return getExecutor()
        }
    }

    @After
    public void tearDown() {
        factory.stop()
    }

    @Test
    public void stopBlocksUntilAllJobsAreComplete() {
        Runnable runnable = context.mock(Runnable.class)

        context.checking {
            one(runnable).run()
            will {
                syncAt(1)
            }
        }

        def executor = factory.create('<display-name>')
        executor.execute(runnable)

        run {
            expectBlocksUntil(1) {
                executor.stop()
            }
        }
    }
    
    @Test
    public void factoryStopBlocksUntilAllJobsAreComplete() {
        Runnable runnable = context.mock(Runnable.class)

        context.checking {
            one(runnable).run()
            will {
                syncAt(1)
            }
        }

        def executor = factory.create('<display-name>')
        executor.execute(runnable)

        run {
            expectBlocksUntil(1) {
                factory.stop()
            }
        }
    }

    @Test
    public void stopThrowsExceptionOnTimeout() {
        Runnable runnable = context.mock(Runnable.class)

        context.checking {
            one(runnable).run()
            will {
                Thread.sleep(1000)
            }
        }

        def executor = factory.create('<display-name>')
        executor.execute(runnable)

        expectTimesOut(500, TimeUnit.MILLISECONDS) {
            try {
                executor.stop(500, TimeUnit.MILLISECONDS)
                org.junit.Assert.fail()
            } catch (IllegalStateException e) {
                org.junit.Assert.assertThat(e.message, org.hamcrest.Matchers.equalTo('Timeout waiting for concurrent jobs to complete.'))
            }
        }
    }

    @Test
    public void cannotStopExecutorFromAnExecutorThread() {
        Runnable runnable = context.mock(Runnable.class)

        def executor = factory.create('<display-name>')

        context.checking {
            one(runnable).run()
            will {
                executor.stop()
            }
        }

        executor.execute(runnable)

        try {
            executor.stop()
            org.junit.Assert.fail()
        } catch (IllegalStateException e) {
            org.junit.Assert.assertThat(e.message, org.hamcrest.Matchers.equalTo('Cannot stop this executor from an executor thread.'))
        }

    }
}
