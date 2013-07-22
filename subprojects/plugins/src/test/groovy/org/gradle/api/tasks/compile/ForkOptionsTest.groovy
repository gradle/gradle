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

import static org.junit.Assert.assertEquals

class ForkOptionsTest {
    static final List PROPS = ['executable', 'memoryInitialSize', 'memoryMaximumSize', 'tempDir']
    
    ForkOptions forkOptions

    @Before public void setUp()  {
        forkOptions = new ForkOptions()
    }

    @Test public void testCompileOptions() {
        forkOptions.with {
            assert executable == null
            assert memoryInitialSize == null
            assert memoryMaximumSize == null
            assert tempDir == null
            assert jvmArgs == []
        }
    }

    @Test public void testOptionMap() {
        Map optionMap = forkOptions.optionMap()
        assertEquals(0, optionMap.size())
        PROPS.each { forkOptions."$it" = "${it}Value" }
        optionMap = forkOptions.optionMap()
        assertEquals(4, optionMap.size())
        PROPS.each { assert optionMap[it] == "${it}Value" as String }
    }

    @Test public void testDefine() {
        forkOptions.define(PROPS.collectEntries { [it, "${it}Value" as String ] })
        PROPS.each { assert forkOptions."${it}" == "${it}Value" as String }
    }
}
