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


package org.gradle.api.internal.tasks.testing


import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JMock
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.testing.TestClassProcessor
import org.junit.After
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.jmock.Sequence
import org.junit.Ignore

@RunWith(JMock.class)
class WorkerTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestClassProcessor target = context.mock(TestClassProcessor.class)
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class)
    private final TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class)
    private final Sequence sequence = context.sequence('seq')
    private final ClassLoader appClassLoader = new ClassLoader() {}
    private ClassLoader original
    private final WorkerTestClassProcessor processor = new WorkerTestClassProcessor(target, 'worker-id', 'worker display name', appClassLoader)

    @Before
    public void setUp() {
        original = Thread.currentThread().contextClassLoader
    }

    @After
    public void tearDown() {
        Thread.currentThread().contextClassLoader = original
    }
    
    @Test
    public void generatesAStartEventOnStartProcessing() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo('worker-id'))
                assertThat(suite.name, equalTo('worker display name'))
            }
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor)
    }

    @Test
    public void generatesACompleteEventOnEndProcessing() {
        context.checking {
            def suite
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal param ->
                suite = param
            }
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)

            one(target).endProcessing()
            inSequence(sequence)

            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            inSequence(sequence)
            will { param, result ->
                assertThat(param, sameInstance(suite))
            }
        }

        processor.startProcessing(resultProcessor)
        processor.endProcessing()
    }

    @Test
    public void setsContextClassLoaderDuringExecution() {
        context.checking {
            ignoring(resultProcessor)
            one(target).startProcessing(resultProcessor)
            will {
                assertThat(Thread.currentThread().contextClassLoader, sameInstance(appClassLoader))
            }
            one(target).processTestClass(runInfo)
            will {
                assertThat(Thread.currentThread().contextClassLoader, sameInstance(appClassLoader))
            }
            one(target).endProcessing()
            will {
                assertThat(Thread.currentThread().contextClassLoader, sameInstance(appClassLoader))
            }
        }

        processor.startProcessing(resultProcessor)
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(original))

        processor.processTestClass(runInfo)
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(original))
        
        processor.endProcessing()
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(original))
    }

    @Test @Ignore
    public void completeEventContainsStartProcessingException() {
        fail("implement me")
    }

    @Test @Ignore
    public void completeEventContainsStopProcessingException() {
        fail("implement me")
    }

    @Test @Ignore
    public void generatesAFailedEventOnFailureToProcessTestClass() {
        fail("implement me")
    }
}

