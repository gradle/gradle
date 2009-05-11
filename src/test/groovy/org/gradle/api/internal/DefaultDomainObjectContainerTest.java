/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.*;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.Task;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.util.GUtil;
import org.gradle.util.TestClosure;
import org.gradle.util.HelperUtil;
import static org.gradle.util.HelperUtil.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;

import java.util.Iterator;

@RunWith(JMock.class)
public class DefaultDomainObjectContainerTest {
    private final DefaultDomainObjectContainer<Bean> container = new DefaultDomainObjectContainer<Bean>();
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void canGetAllDomainObjectsForEmptyContainer() {
        assertTrue(container.getAll().isEmpty());
    }

    @Test
    public void canGetAllDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        assertThat(container.getAll(), equalTo(toLinkedSet(bean1, bean2, bean3)));
    }

    @Test
    public void canIterateOverEmptyContainer() {
        Iterator<Bean> iterator = container.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canIterateOverDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        Iterator<Bean> iterator = container.iterator();
        assertThat(iterator.next(), sameInstance(bean1));
        assertThat(iterator.next(), sameInstance(bean2));
        assertThat(iterator.next(), sameInstance(bean3));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canGetAllDomainObjectsAsMapForEmptyContainer() {
        assertTrue(container.getAsMap().isEmpty());
    }

    @Test
    public void canGetAllDomainObjectsAsMap() {
        Bean bean1 = new Bean();
        Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        container.addObject("b", bean2);
        container.addObject("a", bean1);
        container.addObject("c", bean3);

        assertThat(container.getAsMap(), equalTo(GUtil.map("a", bean1, "b", bean2, "c", bean3)));
    }

    @Test
    public void canGetAllMatchingDomainObjectsOrderedByName() {
        Bean bean1 = new Bean();
        final Bean bean2 = new Bean();
        Bean bean3 = new Bean();

        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return element == bean2;
            }
        };

        container.addObject("a", bean1);
        container.addObject("b", bean2);
        container.addObject("c", bean3);

        assertThat(container.findAll(spec), equalTo(toLinkedSet(bean2)));
    }

    @Test
    public void getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return false;
            }
        };

        container.addObject("a", new Bean());

        assertTrue(container.findAll(spec).isEmpty());
    }

    @Test
    public void canGetDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.getByName("a"), sameInstance(bean));
        assertThat(container.getAt("a"), sameInstance(bean));
    }

    @Test
    public void getDomainObjectByNameFailsForUnknownDomainObject() {
        try {
            container.getByName("unknown");
            fail();
        } catch (UnknownDomainObjectException e) {
            assertThat(e.getMessage(), equalTo("Domain object with name 'unknown' not found."));
        }
    }

    @Test
    public void getDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.getByName("bean"), sameInstance(bean));
    }

    @Test
    public void canConfigureDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.getByName("a", toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void configureDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.getByName("bean", toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void canFindDomainObjectByName() {
        Bean bean = new Bean();
        container.addObject("a", bean);

        assertThat(container.findByName("a"), sameInstance(bean));
    }

    @Test
    public void findDomainObjectByNameReturnsNullForUnknownDomainObject() {
        assertThat(container.findByName("a"), nullValue());
    }

    @Test
    public void findDomainObjectByNameInvokesRulesForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertThat(container.findByName("bean"), sameInstance(bean));
    }

    @Test
    public void canGetAllDomainObjectsOfTypeOrderedByName() {
        class OtherBean extends Bean {}
        Bean bean1 = new Bean();
        OtherBean bean2 = new OtherBean();
        Bean bean3 = new Bean();

        container.addObject("c", bean3);
        container.addObject("a", bean1);
        container.addObject("b", bean2);

        assertThat(container.findByType(Bean.class), equalTo(toLinkedSet(bean1, bean2, bean3)));
        assertThat(container.findByType(OtherBean.class), equalTo(toLinkedSet(bean2)));
    }

    @Test
    public void getAllDomainObjectsOfTypeReturnsEmptySetWhenNoMatches() {
        class OtherBean extends Bean {}
        container.addObject("a", new Bean());

        assertTrue(container.findByType(OtherBean.class).isEmpty());
    }

    @Test
    public void callsActionWhenObjectAdded() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.whenObjectAdded(action);
        container.addObject("bean", bean);
    }

    @Test
    public void callsClosureWhenObjectAdded() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.whenObjectAdded(HelperUtil.toClosure(closure));
        container.addObject("bean", bean);
    }
    
    @Test
    public void callsActionWhenObjectRemoved() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.whenObjectRemoved(action);
        container.addObject("bean", bean);
        container.addObject("bean", new Bean());
    }

    @Test
    public void allObjectsCallsActionForEachExistingObject() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.addObject("bean", bean);
        container.allObjects(action);
    }

    @Test
    public void allObjectsCallsClosureForEachExistingObject() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.addObject("bean", bean);
        container.allObjects(HelperUtil.toClosure(closure));
    }

    @Test
    public void allObjectsCallsActionForEachNewObject() {
        final Action<Bean> action = context.mock(Action.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(action).execute(bean);
        }});

        container.allObjects(action);
        container.addObject("bean", bean);
    }

    @Test
    public void allObjectsCallsClosureForEachNewObject() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Bean bean = new Bean();

        context.checking(new Expectations() {{
            one(closure).call(bean);
        }});

        container.allObjects(HelperUtil.toClosure(closure));
        container.addObject("bean", bean);
    }

    @Test
    public void eachObjectIsAvailableAsADynamicProperty() {
        Bean bean = new Bean();
        container.addObject("child", bean);
        assertTrue(container.getAsDynamicObject().hasProperty("child"));
        assertThat(container.getAsDynamicObject().getProperty("child"), sameInstance((Object) bean));
        assertThat(container.getAsDynamicObject().getProperties().get("child"), sameInstance((Object) bean));
        assertThat(call("{ it.child }", container), sameInstance((Object) bean));
    }

    @Test
    public void eachObjectIsAvailableUsingAnIndex() {
        Bean bean = new Bean();
        container.addObject("child", bean);
        assertThat(call("{ it['child'] }", container), sameInstance((Object) bean));
    }

    @Test
    public void cannotGetUnknownProperty() {
        assertFalse(container.getAsDynamicObject().hasProperty("unknown"));

        try {
            container.getAsDynamicObject().getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            // expected
        }
    }

    @Test
    public void dynamicPropertyAccessInvokesRulesForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertTrue(container.getAsDynamicObject().hasProperty("bean"));
        assertThat(container.getAsDynamicObject().getProperty("bean"), sameInstance((Object) bean));
    }

    @Test
    public void eachObjectIsAvailableAsConfigureMethod() {
        Bean bean = new Bean();
        container.addObject("child", bean);

        Closure closure = toClosure("{ beanProperty = 'value' }");
        assertTrue(container.getAsDynamicObject().hasMethod("child", closure));
        container.getAsDynamicObject().invokeMethod("child", closure);
        assertThat(bean.getBeanProperty(), equalTo("value"));

        call("{ it.child { beanProperty = 'new value' } }", container);
        assertThat(bean.getBeanProperty(), equalTo("new value"));
    }

    @Test
    public void cannotInvokeUnknownMethod() {
        container.addObject("child", new Bean());

        assertMethodUnknown("unknown");
        assertMethodUnknown("unknown", toClosure("{ }"));
        assertMethodUnknown("child");
        assertMethodUnknown("child", "not a closure");
        assertMethodUnknown("child", toClosure("{ }"), "something else");
    }

    private void assertMethodUnknown(String name, Object... arguments) {
        assertFalse(container.getAsDynamicObject().hasMethod(name, arguments));
        try {
            container.getAsDynamicObject().invokeMethod(name, arguments);
            fail();
        } catch (groovy.lang.MissingMethodException e) {
            // Expected
        }
    }

    @Test
    public void configureMethodInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRuleFor(bean);

        assertTrue(container.getAsDynamicObject().hasMethod("bean", toClosure("{ }")));
    }

    private void addRuleFor(final Bean bean) {
        container.addRule(new Rule() {
            public String getDescription() {
                throw new UnsupportedOperationException();
            }

            public void apply(String taskName) {
                container.addObject(taskName, bean);
            }
        });
    }

    private class Bean {
        private String beanProperty;

        public String getBeanProperty() {
            return beanProperty;
        }

        public void setBeanProperty(String beanProperty) {
            this.beanProperty = beanProperty;
        }
    }
    
}
