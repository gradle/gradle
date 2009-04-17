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

import org.gradle.api.specs.Spec;
import org.gradle.api.UnknownDomainObjectException;
import static org.gradle.util.WrapUtil.*;
import org.gradle.util.HelperUtil;
import org.gradle.util.GUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import groovy.lang.Closure;

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
    public void canConfigureDomainObjectByName() {
        Bean bean = new Bean();
        container.add("a", bean);

        container.get("a", HelperUtil.toClosure("{ beanProperty = 'hi' }"));
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
    public void eachObjectIsAvailableAsDynamicProperty() {
        Bean bean = new Bean();
        container.add("child", bean);
        assertTrue(container.getAsDynamicObject().hasProperty("child"));
        assertThat(container.getAsDynamicObject().getProperty("child"), sameInstance((Object) bean));
        assertThat(container.getAsDynamicObject().getProperties().get("child"), sameInstance((Object) bean));
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
