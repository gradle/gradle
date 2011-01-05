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

import groovy.lang.MissingPropertyException;

import java.util.HashMap;
import java.util.Map;

public class MapBackedDynamicObject extends AbstractDynamicObject {
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final AbstractDynamicObject owner;

    public MapBackedDynamicObject(AbstractDynamicObject owner) {
        this.owner = owner;
    }

    @Override
    protected String getDisplayName() {
        return owner.getDisplayName();
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        if (hasProperty(name)) {
            return properties.get(name);
        }
        return super.getProperty(name);
    }

    @Override
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }
}
