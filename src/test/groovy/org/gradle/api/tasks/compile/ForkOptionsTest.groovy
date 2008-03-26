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

/**
 * @author Hans Dockter
 */
class ForkOptionsTest extends GroovyTestCase {
    static final Map PROPS = [executable: 'executable', memoryInitialSize: 'memoryInitialSize', memoryMaximumSize: 'memoryMaximumSize', tempDir: 'tempdir']
    
    ForkOptions forkOptions

    void setUp() {
        forkOptions = new ForkOptions()
    }

    void testCompileOptions() {
        assertNull(forkOptions.executable)
        assertNull(forkOptions.memoryInitialSize)
        assertNull(forkOptions.memoryMaximumSize)
        assertNull(forkOptions.tempDir)
    }

    void testOptionMap() {
        Map optionMap = forkOptions.optionMap()
        assertEquals(0, optionMap.size())
        PROPS.keySet().each { forkOptions."$it" = "${it}Value" }
        optionMap = forkOptions.optionMap()
        assertEquals(4, optionMap.size())
        PROPS.keySet().each {assertEquals("${it}Value", optionMap[PROPS[it]])}
    }

    void testDefine() {
        forkOptions.define(PROPS.keySet().inject([:]) { Map map, String prop ->
            map[prop] = "${prop}Value"
            map
        })
        PROPS.keySet().each {assertEquals("${it}Value" as String, forkOptions."${it}")}
    }
}
