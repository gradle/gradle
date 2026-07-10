/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.metaobject;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.junit.Test;

import javax.annotation.Nonnull;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractDynamicObjectTest {
    private final AbstractDynamicObject object = new AbstractDynamicObject() {
        @Nonnull
        public String getDisplayName() {
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
            assertThat(e.getMessage(), equalTo("Could not get unknown property 'something' for <display-name>."));
        }

        DynamicInvokeResult result = object.tryGetProperty("something");
        assertFalse(result.isFound());

        try {
            object.setProperty("something", "value");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not set unknown property 'something' for <display-name>."));
        }

        result = object.trySetProperty("something", "value");
        assertFalse(result.isFound());
    }

    @Test
    public void hasNoMethods() {
        assertFalse(object.hasMethod("method", "a"));

        try {
            object.invokeMethod("method", "b");
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMessage(), equalTo("Could not find method method() for arguments [b] on <display-name>."));
        }

        DynamicInvokeResult result = object.tryInvokeMethod("method", "c");
        assertFalse(result.isFound());
    }
}
