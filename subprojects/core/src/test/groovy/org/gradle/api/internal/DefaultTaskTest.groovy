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

package org.gradle.api.internal

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.util.WrapUtil
import org.junit.Before
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import java.util.concurrent.Callable
import org.gradle.listener.ListenerManager

/**
 * @author Hans Dockter
 */
class DefaultTaskTest extends AbstractTaskTest {
    DefaultTask defaultTask

    Object testCustomPropValue;

    @Before public void setUp() {
        super.setUp()
        testCustomPropValue = new Object()
        defaultTask = createTask(DefaultTask.class)
    }

    AbstractTask getTask() {
        defaultTask
    }

    @Test public void testDefaultTask() {
        assertThat(defaultTask.dependsOn, isEmpty())
        assertEquals([], defaultTask.actions)
    }

    @Test public void testHasUsefulToString() {
        assertEquals('task \':taskname\'', task.toString())
    }

    @Test public void testCanInjectValuesIntoTaskWhenUsingNoArgsConstructor() {
        DefaultTask task = AbstractTask.injectIntoNewInstance(project, TEST_TASK_NAME, { new DefaultTask() } as Callable)
        assertThat(task.project, sameInstance(project))
        assertThat(task.name, equalTo(TEST_TASK_NAME))
    }

    @Test public void testDoFirstWithClosureDelegatesToTask() {
        Closure testAction = {}
        defaultTask.doFirst(testAction)
        assertSame(defaultTask, testAction.delegate)
        assertEquals(Closure.DELEGATE_FIRST, testAction.getResolveStrategy())
    }

    @Test public void testDoFirstWithClosure() {
        List<Integer> executed = new ArrayList<Integer>();
        Closure testAction1 = { executed.add(1) }
        Closure testAction2 = {-> executed.add(2) }
        Closure testAction3 = {task -> executed.add(3) }
        defaultTask.doFirst(testAction1)
        defaultTask.doFirst(testAction2)
        defaultTask.doFirst(testAction3)
        defaultTask.execute()
        assertEquals(executed, WrapUtil.toList(3, 2, 1))
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
    void getAndSetConventionProperties() {
        TestConvention convention = new TestConvention()
        defaultTask.convention.plugins.test = convention
        assertTrue(defaultTask.hasProperty('conventionProperty'))
        defaultTask.conventionProperty = 'value'
        assertEquals(defaultTask.conventionProperty, 'value')
        assertEquals(convention.conventionProperty, 'value')
    }

    @Test
    void canCallConventionMethods() {
        defaultTask.convention.plugins.test = new TestConvention()
        assertEquals(defaultTask.conventionMethod('a', 'b').toString(), "a.b")
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

    @Test
    void canGetTemporaryDirectory() {
        File tmpDir = new File(project.buildDir, "tmp/taskname")
        assertFalse(tmpDir.exists())

        assertThat(defaultTask.temporaryDir, equalTo(tmpDir))
        assertTrue(tmpDir.isDirectory())
    }

    @Test
    void canAccessServices() {
        assertNotNull(defaultTask.services.get(ListenerManager))
    }
}

class TestConvention {
    def conventionProperty

    def conventionMethod(a, b) {
        "$a.$b"
    }
}
