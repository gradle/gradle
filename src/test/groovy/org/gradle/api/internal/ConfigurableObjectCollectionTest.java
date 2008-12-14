/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import groovy.lang.Closure;

public class ConfigurableObjectCollectionTest {
    private final ConfigurableObjectCollection<Bean> collection = new ConfigurableObjectCollection<Bean>("<owner>");

    @Test
    public void isEmptyByDefault() {
        assertTrue(collection.getAll().isEmpty());
    }

    @Test
    public void canAddAndGetObjectByName() {
        Bean bean = new Bean();
        collection.put("child", bean);
        assertThat(collection.get("child"), sameInstance(bean));
        assertThat(collection.getAll().get("child"), sameInstance(bean));
    }

    @Test
    public void getReturnsNullWhenNoChildWithGivenName() {
        assertThat(collection.get("child"), nullValue());
    }

    @Test
    public void eachObjectIsAvailableAsDynamicProperty() {
        Bean bean = new Bean();
        collection.put("child", bean);
        assertTrue(collection.hasProperty("child"));
        assertThat(collection.getProperty("child"), sameInstance(bean));
        assertThat(collection.getProperties().get("child"), sameInstance(bean));
    }

    @Test
    public void eachObjectIsAvailableAsConfigureMethod() {
        Bean bean = new Bean();
        collection.put("child", bean);
        Closure closure = HelperUtil.toClosure("{ beanProperty = 'value' }");
        assertTrue(collection.hasMethod("child", closure));
        collection.invokeMethod("child", closure);
        assertThat(bean.getBeanProperty(), equalTo("value"));
    }

    @Test
    public void cannotInvokeUnknownMethod() {
        collection.put("child", new Bean());

        assertFalse(collection.hasMethod("unknown", HelperUtil.toClosure("{ }")));
        assertFalse(collection.hasMethod("child"));
        assertFalse(collection.hasMethod("child", "not a closure"));
        assertFalse(collection.hasMethod("child", HelperUtil.toClosure("{ }"), "something else"));
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
