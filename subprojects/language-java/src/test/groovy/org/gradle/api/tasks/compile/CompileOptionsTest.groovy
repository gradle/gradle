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

package org.gradle.api.tasks.compile

import org.junit.Before
import org.junit.Test

import static org.gradle.util.Matchers.isEmpty
import static org.junit.Assert.*

class CompileOptionsTest {
    static final Map TEST_DEBUG_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']
    static final Map TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    CompileOptions compileOptions

    @Before public void setUp()  {
        compileOptions = new CompileOptions()
        compileOptions.debugOptions = [optionMap: {TEST_DEBUG_OPTION_MAP}] as DebugOptions
        compileOptions.forkOptions = [optionMap: {TEST_FORK_OPTION_MAP}] as ForkOptions
    }

    @Test public void testCompileOptions() {
        assertTrue(compileOptions.debug)
        assertTrue(compileOptions.failOnError)
        assertTrue(compileOptions.warnings)

        assertFalse(compileOptions.deprecation)
        assertFalse(compileOptions.listFiles)
        assertFalse(compileOptions.verbose)
        assertFalse(compileOptions.fork)

        assertThat(compileOptions.compilerArgs, isEmpty())
        assertNull(compileOptions.encoding)
        assertNull(compileOptions.bootClasspath)
        assertNull(compileOptions.extensionDirs)

        assertNotNull(compileOptions.forkOptions)
        assertNotNull(compileOptions.debugOptions)
    }

    @Test public void testOptionMapForDebugOptions() {
        Map optionMap = compileOptions.optionMap()
        assertEquals(optionMap.subMap(TEST_DEBUG_OPTION_MAP.keySet()), TEST_DEBUG_OPTION_MAP)
        assertEquals(optionMap.subMap(TEST_FORK_OPTION_MAP.keySet()), TEST_FORK_OPTION_MAP)
    }

    @Test public void testOptionMapWithNullables() {
        Map optionMap = compileOptions.optionMap()
        Map nullables = [
                encoding: 'encoding',
                bootClasspath: 'bootClasspath',
                extensionDirs: 'extdirs'
        ]
        nullables.each {String field, String antProperty ->
            assertFalse(optionMap.keySet().contains(antProperty))
        }

        nullables.keySet().each {compileOptions."$it" = "${it}Value"}
        optionMap = compileOptions.optionMap()
        nullables.each {String field, String antProperty ->
            assertEquals("${field}Value" as String, optionMap[antProperty])
        }
    }

    @Test public void testOptionMapWithTrueFalseValues() {
        Map booleans = [
                failOnError: 'failOnError',
                verbose: 'verbose',
                listFiles: 'listFiles',
                deprecation: 'deprecation',
                warnings: 'nowarn',
                debug: 'debug'
        ]
        booleans.keySet().each {compileOptions."$it" = true}
        Map optionMap = compileOptions.optionMap()
        booleans.values().each {
            if (it.equals('nowarn')) {
                assertEquals(false, optionMap[it])
            } else {
                assertEquals(true, optionMap[it])
            }
        }
        booleans.keySet().each {compileOptions."$it" = false}
        optionMap = compileOptions.optionMap()
        booleans.values().each {
            if (it.equals('nowarn')) {
                assertEquals(true, optionMap[it])
            } else {
                assertEquals(false, optionMap[it])
            }
        }
    }

    @Test public void testWithExcludeFieldsFromOptionMap() {
      compileOptions.compilerArgs = [[value: 'something']]
        Map optionMap = compileOptions.optionMap()
        ['debugOptions', 'forkOptions', 'compilerArgs'].each {
            assertFalse(optionMap.containsKey(it))
        }
    }

    @Test public void testFork() {
        compileOptions.fork = false
        boolean forkUseCalled = false
        compileOptions.forkOptions = [define: {Map args ->
            forkUseCalled = true
            assertEquals(TEST_FORK_OPTION_MAP, args)
        }] as ForkOptions
        assert compileOptions.fork(TEST_FORK_OPTION_MAP).is(compileOptions)
        assertTrue(compileOptions.fork)
        assertTrue(forkUseCalled)
    }

    @Test public void testDebug() {
        compileOptions.debug = false
        boolean debugUseCalled = false
        compileOptions.debugOptions = [define: {Map args ->
            debugUseCalled = true
            assertEquals(TEST_DEBUG_OPTION_MAP, args)
        }] as DebugOptions
        assert compileOptions.debug(TEST_DEBUG_OPTION_MAP).is(compileOptions)
        assertTrue(compileOptions.debug)
        assertTrue(debugUseCalled)
    }

    @Test public void testDefine() {
        compileOptions.debug = false
        compileOptions.bootClasspath = 'xxxx'
        compileOptions.fork = false
        compileOptions.define(debug: true, bootClasspath: null)
        assertTrue(compileOptions.debug)
        assertNull(compileOptions.bootClasspath)
        assertFalse(compileOptions.fork)
    }
}
