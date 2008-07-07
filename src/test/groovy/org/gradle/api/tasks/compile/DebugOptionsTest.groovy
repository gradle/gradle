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

import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class DebugOptionsTest {
    static final String TEST_DEBUG_LEVEL = 'testDebugLevel'
    static final String DEBUG_LEVEL_PROPERTY_NAME = 'debugLevel'
    static final String DEBUG_LEVEL_ANT_PROPERTY_NAME = 'debuglevel'

    DebugOptions debugOptions

    @Before public void setUp()  {
        debugOptions = new DebugOptions()
    }

    @Test public void testDebugOptions() {
        assertNull(debugOptions.debugLevel)
    }

    @Test public void testOptionMap() {
        Map optionMap = debugOptions.optionMap()
        assertEquals(0, optionMap.size())

        debugOptions.debugLevel = TEST_DEBUG_LEVEL
        optionMap = debugOptions.optionMap()
        assertEquals(1, optionMap.size())
        assertEquals(optionMap[DEBUG_LEVEL_ANT_PROPERTY_NAME], TEST_DEBUG_LEVEL)
    }

    @Test public void testDefine() {
        debugOptions.debugLevel = null
        debugOptions.define((DEBUG_LEVEL_PROPERTY_NAME): TEST_DEBUG_LEVEL)
        assertEquals(TEST_DEBUG_LEVEL, debugOptions.debugLevel)
    }
}
