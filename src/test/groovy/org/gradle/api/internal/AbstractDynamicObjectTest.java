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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import groovy.lang.*;
import groovy.lang.MissingMethodException;

public class AbstractDynamicObjectTest {
    private final AbstractDynamicObject object = new AbstractDynamicObject() {
        protected String getDisplayName() {
            return "<display-name>";
        }
    };

    @Test
    public void hasNoProperties() {
        assertFalse(object.hasProperty("something"));
        assertTrue(object.getProperties().isEmpty());

        try {
            object.getProperty("something");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not find property 'something' on <display-name>."));
        }

        try {
            object.setProperty("something", "value");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not find property 'something' on <display-name>."));
        }
    }

    @Test
    public void hasNoMethods() {
        assertFalse(object.hasMethod("method", "a"));

        try {
            object.invokeMethod("method", "b");
            fail();
        } catch (MissingMethodException e) {
            // Expected
        }
    }
}
