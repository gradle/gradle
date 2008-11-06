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

import org.gradle.api.internal.DefaultTask
import org.gradle.api.tasks.AbstractTaskTest
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class DefaultTaskTest extends AbstractTaskTest {
    DefaultTask defaultTask

    Object testCustomPropValue;

    @Before public void setUp()  {
        super.setUp()
        testCustomPropValue = new Object()
        defaultTask = new DefaultTask(project, AbstractTaskTest.TEST_TASK_NAME)
    }

    AbstractTask getTask() {
        defaultTask
    }

    @Test public void testDefaultTask() {
        assertEquals new TreeSet(), defaultTask.dependsOn
        assertEquals([], defaultTask.actions)
    }

    @Test public void testDoFirstWithClosure() {
        Closure testAction = {}
        defaultTask.doFirst(testAction)
        assertSame(getProject(), testAction.delegate)
        assertEquals(Closure.OWNER_FIRST, testAction.getResolveStrategy())
    }

    @Test
    void getAdditonalProperties() {
        defaultTask.additionalProperties.customProp = testCustomPropValue
        assertSame(testCustomPropValue, defaultTask."customProp")
    }

    @Test
    void setAdditonalProperties() {
        defaultTask."customProp" = testCustomPropValue
        assertSame(testCustomPropValue, defaultTask.additionalProperties.customProp)
    }

    @Test
    void getProperty() {
        defaultTask.additionalProperties.customProp = testCustomPropValue
        assertSame(testCustomPropValue, defaultTask.property("customProp"))
        assertSame(AbstractTaskTest.TEST_TASK_NAME, defaultTask.property("name"))
    }

    @Test(expected = MissingPropertyException)
    void accessNonExistingProperty() {
        defaultTask."unknownProp"
    }


}