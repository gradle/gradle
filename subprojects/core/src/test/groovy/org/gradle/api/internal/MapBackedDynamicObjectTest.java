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

import org.junit.Test;

import java.util.Map;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class MapBackedDynamicObjectTest {
    private final MapBackedDynamicObject object = new MapBackedDynamicObject(null);

    @Test
    public void hasNoPropertiesByDefault() {
        assertTrue(object.getProperties().isEmpty());
        assertFalse(object.hasProperty("someProp"));
    }

    @Test
    public void canAddAProperty() {
        object.setProperty("someProp", "value");
        assertThat(object.getProperties(), equalTo((Map) toMap("someProp", (Object) "value")));
        assertTrue(object.hasProperty("someProp"));
        assertThat(object.getProperty("someProp"), equalTo((Object) "value"));
    }

    @Test
    public void canChangeAPropertyValue() {
        object.setProperty("someProp", "value");
        assertThat(object.getProperty("someProp"), equalTo((Object) "value"));

        object.setProperty("someProp", "new value");
        assertThat(object.getProperty("someProp"), equalTo((Object) "new value"));
    }
}
