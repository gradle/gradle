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
 
package org.gradle.api.tasks.testing

import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class JunitForkOptionsTest {
    JunitForkOptions junitForkOptions

    @Before public void setUp()  {
        junitForkOptions = new JunitForkOptions()
    }

    @Test public void testJunitForkOptions() {
        assertFalse(junitForkOptions.newEnvironment)
        assertFalse(junitForkOptions.cloneVm)

        assertEquals(ForkMode.PER_TEST, junitForkOptions.forkMode)

        assertNull(junitForkOptions.timeout)
        assertNull(junitForkOptions.maxMemory)
        assertNull(junitForkOptions.jvm)
        assertNull(junitForkOptions.dir)
    }

    @Test public void testOptionMapWithDir() {
        Map optionMap = junitForkOptions.optionMap()
        assertFalse(optionMap.keySet().contains('dir'))
        junitForkOptions.dir = 'dirFile' as File
        assertEquals(junitForkOptions.dir, junitForkOptions.optionMap()['dir'])
    }

    @Test public void testOptionMapWithNullables() {
        Map optionMap = junitForkOptions.optionMap()
        Map nullables = [
                timeout: 'timeout',
                maxMemory: 'maxmemory',
                jvm: 'jvm'
        ]
        nullables.each {String field, String antProperty ->
            assertFalse(optionMap.keySet().contains(antProperty))
        }

        nullables.keySet().each {junitForkOptions."$it" = "${it}Value"}
        optionMap = junitForkOptions.optionMap()
        nullables.each {String field, String antProperty ->
            assertEquals(field + "Value", optionMap[antProperty])
        }
    }

    @Test public void testOptionMapWithForkMode() {
        assertEquals(ForkMode.PER_TEST.toString(), junitForkOptions.optionMap()['forkmode'])
    }

    @Test public void testOptionMapWithTrueFalseValues() {
        Map booleans = [
                newEnvironment: 'newenvironment',
                cloneVm: 'clonevm',
        ]
        booleans.keySet().each {junitForkOptions."$it" = true}
        Map optionMap = junitForkOptions.optionMap()
        booleans.values().each {
            assertEquals(true, optionMap[it])
        }
        booleans.keySet().each {junitForkOptions."$it" = false}
        optionMap = junitForkOptions.optionMap()
        booleans.values().each {
            assertEquals(false, optionMap[it])
        }
    }

    @Test public void testDefine() {
        junitForkOptions.maxMemory = null
        junitForkOptions.newEnvironment = true
        junitForkOptions.timeout = 'xxxx'
        junitForkOptions.define(newEnvironment: false, maxMemory: 'xxx', timeout: null)
        assertFalse(junitForkOptions.newEnvironment)
        assertEquals('xxx', junitForkOptions.maxMemory)
        assertNull(junitForkOptions.timeout)
    }
}
