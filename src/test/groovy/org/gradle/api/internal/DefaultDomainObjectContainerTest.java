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
import org.gradle.api.specs.Spec;
import org.gradle.util.GUtil;
import static org.gradle.util.HelperUtil.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Iterator;

public class DefaultDomainObjectContainerTest {
    private final DefaultDomainObjectContainer<Bean> container = new DefaultDomainObjectContainer<Bean>();

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
        addRule(bean);

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
        addRule(bean);

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
        addRule(bean);

        assertThat(container.findByName("bean"), sameInstance(bean));
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
        addRule(bean);

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
        addRule(bean);

        assertTrue(container.getAsDynamicObject().hasMethod("bean", toClosure("{ }")));
    }

    private void addRule(final Bean bean) {
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
