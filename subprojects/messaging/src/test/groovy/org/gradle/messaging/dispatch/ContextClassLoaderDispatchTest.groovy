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
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class ContextClassLoaderDispatchTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Dispatch<String> target = context.mock(Dispatch.class)
    private final ClassLoader appClassLoader = new ClassLoader() {}
    private ClassLoader original
    private final ContextClassLoaderDispatch dispatch = new ContextClassLoaderDispatch(target, appClassLoader)

    @Before
    public void setUp() {
        original = Thread.currentThread().contextClassLoader
    }

    @After
    public void tearDown() {
        Thread.currentThread().contextClassLoader = original
    }

    @Test
    public void setsContextClassLoaderDuringDispatch() {
        context.checking {
            one(target).dispatch('message')
            will {
                assertThat(Thread.currentThread().contextClassLoader, sameInstance(appClassLoader))
            }
        }

        dispatch.dispatch('message')
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(original))
    }

    @Test
    public void cleansUpAfterFailure() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(target).dispatch('message')
            will {
                assertThat(Thread.currentThread().contextClassLoader, sameInstance(appClassLoader))
                throw failure
            }
        }

        try {
            dispatch.dispatch('message')
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }

        assertThat(Thread.currentThread().contextClassLoader, sameInstance(original))
    }
}
