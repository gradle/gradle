/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.tasks.testing.junit

import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.FormatterOptions
import org.gradle.api.tasks.testing.JunitForkOptions
import org.gradle.api.tasks.testing.AbstractTestFrameworkOptionsTest
import org.gradle.external.junit.JUnitTestFramework;

/**
 * @author Hans Dockter
 */
class JUnitOptionsTest extends AbstractTestFrameworkOptionsTest<JUnitTestFramework> {
    static final Map TEST_FORMATTER_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']
    static final Map TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    JUnitOptions junitOptions

    @Before public void setUp()  {
        super.setUp(JUnitTestFramework);

        junitOptions = new JUnitOptions(testFrameworkMock)
        junitOptions.formatterOptions  = [optionMap: {TEST_FORMATTER_OPTION_MAP}] as FormatterOptions
        junitOptions.forkOptions = [optionMap: {TEST_FORK_OPTION_MAP}] as JunitForkOptions
    }

    @Test public void testCompileOptions() {
        junitOptions = new JUnitOptions(testFrameworkMock)
        assertTrue(junitOptions.fork)
        assertTrue(junitOptions.filterTrace)
        assertTrue(junitOptions.reloading)
        assertTrue(junitOptions.outputToFormatters)

        assertFalse(junitOptions.showOutput)

        assertNull(junitOptions.tempDir)
        assertEquals('true', junitOptions.printSummary)

        assertNotNull(junitOptions.forkOptions)
        assertNotNull(junitOptions.formatterOptions)

        assertEquals([:], junitOptions.systemProperties)
        assertEquals([:], junitOptions.environment)
    }

    @Test public void testOptionMapForFromatterAndForkOptions() {
        Map optionMap = junitOptions.optionMap()
        TEST_FORMATTER_OPTION_MAP.keySet().each { assertFalse(optionMap.containsKey(it)) }
        assertEquals(TEST_FORK_OPTION_MAP, optionMap.subMap(TEST_FORK_OPTION_MAP.keySet()))
    }

    @Test public void testOptionMapWithNullables() {
        junitOptions.printSummary = null
        Map optionMap = junitOptions.optionMap()
        Map nullables = [
                tempDir: 'tempdir',
                printSummary: 'printsummary',
        ]
        nullables.each {String field, String antProperty ->
            assertFalse(optionMap.keySet().contains(antProperty))
        }

        nullables.keySet().each {junitOptions."$it" = "${it}Value"}
        optionMap = junitOptions.optionMap()
        nullables.each {String field, String antProperty ->
            assertEquals(field + "Value", optionMap[antProperty])
        }
    }

    @Test public void testOptionMapWithTrueFalseValues() {
        Map booleans = [
                fork: 'fork',
                filterTrace: 'filtertrace',
                reloading: 'reloading',
                outputToFormatters: 'outputtoformatters',
                showOutput: 'showoutput'
        ]
        booleans.keySet().each {junitOptions."$it" = true}
        Map optionMap = junitOptions.optionMap()
        booleans.values().each {
            assertEquals("true", optionMap[it])
        }
        booleans.keySet().each {junitOptions."$it" = false}
        optionMap = junitOptions.optionMap()
        booleans.values().each {
            assertEquals("false", optionMap[it])
        }
    }

    @Test public void testFork() {
        junitOptions.fork = false
        boolean forkUseCalled = false
        junitOptions.forkOptions = [define: {Map args ->
            forkUseCalled = true
            assertEquals(TEST_FORK_OPTION_MAP, args)
        }] as JunitForkOptions
        assert junitOptions.fork(TEST_FORK_OPTION_MAP).is(junitOptions)
        assertTrue(junitOptions.fork)
        assertTrue(forkUseCalled)
    }

    @Test public void testDefine() {
        junitOptions.fork = false
        junitOptions.tempDir = null
        junitOptions.printSummary = 'xxxx'
        junitOptions.define(fork: true, tempDir: 'tmp', printSummary: null, showOutput: true)
        assertTrue(junitOptions.showOutput)
        assertEquals('tmp', junitOptions.tempDir)
        assertNull(junitOptions.printSummary)
        assertTrue(junitOptions.fork)
    }
}
