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

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
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

        container.add("b", bean2);
        container.add("a", bean1);
        container.add("c", bean3);

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

        container.add("b", bean2);
        container.add("a", bean1);
        container.add("c", bean3);

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

        container.add("b", bean2);
        container.add("a", bean1);
        container.add("c", bean3);

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

        container.add("a", bean1);
        container.add("b", bean2);
        container.add("c", bean3);

        assertThat(container.get(spec), equalTo(toLinkedSet(bean2)));
    }

    @Test
    public void getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        Spec<Bean> spec = new Spec<Bean>() {
            public boolean isSatisfiedBy(Bean element) {
                return false;
            }
        };

        container.add("a", new Bean());

        assertTrue(container.get(spec).isEmpty());
    }

    @Test
    public void canGetDomainObjectByName() {
        Bean bean = new Bean();
        container.add("a", bean);

        assertThat(container.get("a"), sameInstance(bean));
        assertThat(container.getAt("a"), sameInstance(bean));
    }

    @Test
    public void getDomainObjectByNameFailsForUnknownDomainObject() {
        try {
            container.get("unknown");
            fail();
        } catch (UnknownDomainObjectException e) {
            assertThat(e.getMessage(), equalTo("Domain object with name 'unknown' not found."));
        }
    }

    @Test
    public void getDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRule(bean);

        assertThat(container.get("bean"), sameInstance(bean));
    }

    @Test
    public void canConfigureDomainObjectByName() {
        Bean bean = new Bean();
        container.add("a", bean);

        assertThat(container.get("a", HelperUtil.toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void configureDomainObjectInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRule(bean);

        assertThat(container.get("bean", HelperUtil.toClosure("{ beanProperty = 'hi' }")), sameInstance(bean));
        assertThat(bean.getBeanProperty(), equalTo("hi"));
    }

    @Test
    public void canFindDomainObjectByName() {
        Bean bean = new Bean();
        container.add("a", bean);

        assertThat(container.find("a"), sameInstance(bean));
    }

    @Test
    public void findDomainObjectByNameReturnsNullForUnknownDomainObject() {
        assertThat(container.find("a"), nullValue());
    }

    @Test
    public void findDomainObjectByNameInvokesRulesForUnknownDomainObject() {
        Bean bean = new Bean();
        addRule(bean);

        assertThat(container.find("bean"), sameInstance(bean));
    }

    @Test
    public void eachObjectIsAvailableAsDynamicProperty() {
        Bean bean = new Bean();
        container.add("child", bean);
        assertTrue(container.getAsDynamicObject().hasProperty("child"));
        assertThat(container.getAsDynamicObject().getProperty("child"), sameInstance((Object) bean));
        assertThat(container.getAsDynamicObject().getProperties().get("child"), sameInstance((Object) bean));
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
        container.add("child", bean);
        Closure closure = HelperUtil.toClosure("{ beanProperty = 'value' }");
        assertTrue(container.getAsDynamicObject().hasMethod("child", closure));
        container.getAsDynamicObject().invokeMethod("child", closure);
        assertThat(bean.getBeanProperty(), equalTo("value"));
    }

    @Test
    public void cannotInvokeUnknownMethod() {
        container.add("child", new Bean());

        assertFalse(container.getAsDynamicObject().hasMethod("unknown", HelperUtil.toClosure("{ }")));
        assertFalse(container.getAsDynamicObject().hasMethod("child"));
        assertFalse(container.getAsDynamicObject().hasMethod("child", "not a closure"));
        assertFalse(container.getAsDynamicObject().hasMethod("child", HelperUtil.toClosure("{ }"), "something else"));
    }

    @Test
    public void configureMethodInvokesRuleForUnknownDomainObject() {
        Bean bean = new Bean();
        addRule(bean);

        assertTrue(container.getAsDynamicObject().hasMethod("bean", HelperUtil.toClosure("{ }")));
    }

    private void addRule(final Bean bean) {
        container.addRule(new Rule() {
            public String getDescription() {
                throw new UnsupportedOperationException();
            }

            public void apply(String taskName) {
                container.add(taskName, bean);
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
