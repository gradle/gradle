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

package org.gradle.api.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.TestTask
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
class ConventionAwareHelperTest {
    ConventionAwareHelper conventionAware

    TestTask testTask

    @Before public void setUp() {
        testTask = new TestTask(new DefaultProject(), 'somename')
        conventionAware = new ConventionAwareHelper(testTask)
    }

    @Test public void testConventionMapping() {
        List expectedList1 = ['a']
        List expectedList2 = ['b']
        assert testTask.is(conventionAware.conventionMapping([list1: {expectedList1}]))
        assert testTask.is(conventionAware.conventionMapping([list2: {expectedList2}]))
        assert conventionAware.getValue('list1').is(expectedList1)
        assert conventionAware.getValue('list2').is(expectedList2)
    }

    @Test (expected = InvalidUserDataException) public void testIllegalMapping() {
        conventionAware.conventionMapping([unknownProp: {}])
    }

    @Test public void testOverwriteProperties() {
        List conventionList1 = ['a']
        conventionAware.conventionMapping([list1: {conventionList1}])
        assert conventionAware.getValue('list1').is(conventionList1)
        List expectedList1 = ['b']
        testTask.list1 = expectedList1
        assertEquals(expectedList1, conventionAware.getValue('list1'))
    }

    @Test public void testCachedProperties() {
        conventionAware.conventionMapping([list1: {['a']}])
        def value1 = conventionAware.getValue('list1')
        def value2 = conventionAware.getValue('list1')
        assert value1.is(value2)
    }
}
