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

import groovy.lang.*;
import groovy.lang.MissingMethodException;

import java.util.Map;
import java.util.Collections;

public abstract class AbstractDynamicObject implements DynamicObject {
    public boolean hasProperty(String name) {
        return false;
    }

    public Object property(String name) throws MissingPropertyException {
        throw new MissingPropertyException(String.format("Could not find property '%s'.", name));
    }

    public void setProperty(String name, Object value) throws MissingPropertyException {
        throw new MissingPropertyException(String.format("Could not find property '%s'.", name));
    }

    public Map<String, Object> properties() {
        return Collections.emptyMap();
    }

    public boolean hasMethod(String name, Object... params) {
        return false;
    }

    public Object invokeMethod(String name, Object... params) throws MissingMethodException {
        throw new MissingMethodException(name, getClass(), params);
    }
}
