/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.model.ObjectFactory
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class GroovyCompileOptionsTest {
    static final Map TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    GroovyCompileOptions compileOptions

    @Before public void setUp()  {
        ServiceLookup services = new DefaultServiceRegistry().add(ObjectFactory, TestUtil.objectFactory()).add(InstantiatorFactory, TestUtil.instantiatorFactory())
        compileOptions = TestUtil.instantiatorFactory().decorateLenient(services).newInstance(GroovyCompileOptions.class)
        compileOptions.forkOptions = [optionMap: {TEST_FORK_OPTION_MAP}] as GroovyForkOptions
    }

    @Test public void testCompileOptions() {
        assertTrue(compileOptions.failOnError)
        assertFalse(compileOptions.listFiles)
        assertFalse(compileOptions.verbose)
        assertTrue(compileOptions.fork)
        assertEquals(['java', 'groovy'], compileOptions.fileExtensions)
        assertEquals('UTF-8', compileOptions.encoding)
        assertNotNull(compileOptions.forkOptions)
        assertNull(compileOptions.configurationScript)
        assertFalse(compileOptions.javaAnnotationProcessing)
        assertFalse(compileOptions.parameters)
    }

    @Test public void testFork() {
        compileOptions.fork = false
        boolean forkUseCalled = false
        compileOptions.forkOptions = [define: {Map args ->
            forkUseCalled = true
            assertEquals(TEST_FORK_OPTION_MAP, args)
        }] as GroovyForkOptions
        assert compileOptions.fork(TEST_FORK_OPTION_MAP).is(compileOptions)
        assertTrue(compileOptions.fork)
        assertTrue(forkUseCalled)
    }

    @Test public void testDefine() {
        compileOptions.verbose = false
        compileOptions.encoding = 'xxxx'
        compileOptions.fork = false
        compileOptions.parameters = true
        compileOptions.define( encoding: 'encoding')
        assertEquals('encoding', compileOptions.encoding)
        assertFalse(compileOptions.verbose)
        assertFalse(compileOptions.fork)
        assertTrue(compileOptions.parameters)
    }
}
