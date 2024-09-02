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

import java.util.concurrent.atomic.AtomicReference

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class GroovyCompileOptionsTest {
    GroovyCompileOptions compileOptions

    @Before
    void setUp()  {
        ServiceLookup services = new DefaultServiceRegistry().add(ObjectFactory, TestUtil.objectFactory()).add(InstantiatorFactory, TestUtil.instantiatorFactory())
        compileOptions = TestUtil.instantiatorFactory().decorateLenient(services).newInstance(GroovyCompileOptions.class)
    }

    @Test
    void testCompileOptions() {
        assertTrue(compileOptions.failOnError.get())
        assertFalse(compileOptions.listFiles.get())
        assertFalse(compileOptions.verbose.get())
        assertTrue(compileOptions.fork.get())
        assertEquals(['java', 'groovy'], compileOptions.fileExtensions.get())
        assertEquals('UTF-8', compileOptions.encoding.get())
        assertNotNull(compileOptions.forkOptions)
        assertNull(compileOptions.configurationScript.asFile.orNull)
        assertFalse(compileOptions.javaAnnotationProcessing.get())
        assertFalse(compileOptions.parameters.get())
    }

    @Test
    void testFork() {
        compileOptions.fork = false
        assertNull(compileOptions.forkOptions.memoryMaximumSize.getOrNull())

        compileOptions.fork([memoryMaximumSize: '1g'])
        assertTrue(compileOptions.fork.get())
        assertEquals(compileOptions.forkOptions.memoryMaximumSize.get(), '1g')
    }

    @Test
    void testDefine() {
        compileOptions.verbose = false
        compileOptions.encoding = 'xxxx'
        compileOptions.fork = false
        compileOptions.parameters = true
        compileOptions.define( encoding: 'encoding')
        assertEquals('encoding', compileOptions.encoding.get())
        assertFalse(compileOptions.verbose.get())
        assertFalse(compileOptions.fork.get())
        assertTrue(compileOptions.parameters.get())
    }

    @Test
    void "forkOptions closure"() {
        AtomicReference<ForkOptions> forkOptions = new AtomicReference<ForkOptions>()
        compileOptions.forkOptions(forkOptions::set)
        assertEquals(compileOptions.forkOptions, forkOptions.get())
    }
}
