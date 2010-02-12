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
 
package org.gradle.api.tasks.testing.junit

import org.gradle.api.tasks.testing.AbstractTestFrameworkOptionsTest
import org.gradle.api.tasks.testing.FormatterOptions
import org.gradle.external.junit.JUnitTestFramework
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class JUnitOptionsTest extends AbstractTestFrameworkOptionsTest<JUnitTestFramework> {
    static final Map TEST_FORMATTER_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']

    JUnitOptions junitOptions

    @Before public void setUp()  {
        super.setUp(JUnitTestFramework);

        junitOptions = new JUnitOptions(testFrameworkMock)
        junitOptions.formatterOptions  = [optionMap: {TEST_FORMATTER_OPTION_MAP}] as FormatterOptions
    }

    @Test public void testCompileOptions() {
        junitOptions = new JUnitOptions(testFrameworkMock)
        assertTrue(junitOptions.filterTrace)
        assertTrue(junitOptions.outputToFormatters)

        assertFalse(junitOptions.showOutput)

        assertEquals('true', junitOptions.printSummary)

        assertNotNull(junitOptions.formatterOptions)
    }

    @Test public void testOptionMapForFormatterAndForkOptions() {
        Map optionMap = junitOptions.optionMap()
        TEST_FORMATTER_OPTION_MAP.keySet().each { assertFalse(optionMap.containsKey(it)) }
    }

    @Test public void testOptionMapWithNullables() {
        junitOptions.printSummary = null
        Map optionMap = junitOptions.optionMap()
        Map nullables = [
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
                filterTrace: 'filtertrace',
                outputToFormatters: 'outputtoformatters',
                showOutput: 'showoutput'
        ]
        booleans.keySet().each {junitOptions."$it" = true}
        Map optionMap = junitOptions.optionMap()
        booleans.values().each {
            assertEquals(true, optionMap[it])
        }
        booleans.keySet().each {junitOptions."$it" = false}
        optionMap = junitOptions.optionMap()
        booleans.values().each {
            assertEquals(false, optionMap[it])
        }
    }

    @Test public void testDefine() {
        junitOptions.printSummary = 'xxxx'
        junitOptions.define(printSummary: null, showOutput: true)
        assertTrue(junitOptions.showOutput)
        assertNull(junitOptions.printSummary)
    }
}
