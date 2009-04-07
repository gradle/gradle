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

//import org.gradle.api.tasks.AbstractTaskTest
/**
 * @author Hans Dockter
 */
//class DefaultTaskTest extends AbstractTaskTest {
//    DefaultTask defaultTask
//
//    Object testCustomPropValue;
//
//    @Before public void setUp()  {
//        super.setUp()
//        testCustomPropValue = new Object()
//        defaultTask = new DefaultTask(project, AbstractTaskTest.TEST_TASK_NAME)
//    }
//
//    AbstractTask getTask() {
//        defaultTask
//    }
//
//    @Test public void testDefaultTask() {
//        assertEquals new TreeSet(), defaultTask.dependsOn
//        assertEquals([], defaultTask.actions)
//    }
//
//    @Test public void testHasUsefulToString() {
//        assertEquals('task \':taskname\'', task.toString())
//    }
//
//    @Test public void testDoFirstWithClosureDelegatesToProject() {
//        Closure testAction = {}
//        defaultTask.doFirst(testAction)
//        assertSame(getProject(), testAction.delegate)
//        assertEquals(Closure.OWNER_FIRST, testAction.getResolveStrategy())
//    }
//
//    @Test public void testDoFirstWithClosure() {
//        List<Integer> executed = new ArrayList<Integer>();
//        Closure testAction1 = { executed.add(1) }
//        Closure testAction2 = { -> executed.add(2) }
//        Closure testAction3 = { task -> executed.add(3) }
//        defaultTask.doFirst(testAction1)
//        defaultTask.doFirst(testAction2)
//        defaultTask.doFirst(testAction3)
//        defaultTask.execute()
//        assertEquals(executed, WrapUtil.toList(3, 2, 1))
//    }
//
//    @Test
//    void getAdditonalProperties() {
//        defaultTask.additionalProperties.customProp = testCustomPropValue
//        assertSame(testCustomPropValue, defaultTask."customProp")
//    }
//
//    @Test
//    void setAdditonalProperties() {
//        defaultTask."customProp" = testCustomPropValue
//        assertSame(testCustomPropValue, defaultTask.additionalProperties.customProp)
//    }
//
//    @Test
//    void getAndSetConventionProperties() {
//        TestConvention convention = new TestConvention()
//        defaultTask.convention.plugins.test = convention
//        assertTrue(defaultTask.hasProperty('conventionProperty'))
//        defaultTask.conventionProperty = 'value'
//        assertEquals(defaultTask.conventionProperty, 'value')
//        assertEquals(convention.conventionProperty, 'value')
//    }
//
//    @Test
//    void canCallConventionMethods() {
//        defaultTask.convention.plugins.test = new TestConvention()
//        assertEquals(defaultTask.conventionMethod('a', 'b').toString(), "a.b")
//    }
//
//    @Test
//    void getProperty() {
//        defaultTask.additionalProperties.customProp = testCustomPropValue
//        assertSame(testCustomPropValue, defaultTask.property("customProp"))
//        assertSame(AbstractTaskTest.TEST_TASK_NAME, defaultTask.property("name"))
//    }
//
//    @Test(expected = MissingPropertyException)
//    void accessNonExistingProperty() {
//        defaultTask."unknownProp"
//    }
//}

class TestConvention {
    def conventionProperty

    def conventionMethod(a, b) {
        "$a.$b"
    }
}
