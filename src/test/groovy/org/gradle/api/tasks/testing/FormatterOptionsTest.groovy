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

import org.junit.Before
import static org.junit.Assert.*
import org.junit.Test;


/**
 * @author Hans Dockter
 */
class FormatterOptionsTest {
    static final Map PROPS = [extension: 'extension', type: 'type', classname: 'classname']

    FormatterOptions formatterOptions

    @Before public void setUp()  {
        formatterOptions = new FormatterOptions()
    }

    @Test public void testCompileOptions() {
        assertNull(formatterOptions.extension)
        assertEquals('xml', formatterOptions.type)
        assertNull(formatterOptions.classname)
    }

    @Test public void testOptionMap() {
        formatterOptions.type = null
        Map optionMap = formatterOptions.optionMap()
        assertEquals(0, optionMap.size())
        PROPS.keySet().each { formatterOptions."$it" = "${it}Value" }
        optionMap = formatterOptions.optionMap()
        assertEquals(3, optionMap.size())
        PROPS.keySet().each {assertEquals("${it}Value" as String, optionMap[PROPS[it]])}
    }

    @Test public void testDefine() {
        formatterOptions.define(PROPS.keySet().inject([:]) { Map map, String prop ->
            map[prop] = "${prop}Value"
            map
        })
        PROPS.keySet().each {assertEquals("${it}Value" as String, formatterOptions."${it}")}
    }
}
