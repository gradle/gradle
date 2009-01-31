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

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.util.ConfigureUtil;

import java.util.Map;
import java.util.TreeMap;

/**
 * A collection of configurable objects indexed by name.
 */
public class ConfigurableObjectCollection<T> extends AbstractDynamicObject {
    private final String ownerDisplayName;
    private final Map<String, T> elements = new TreeMap<String,T>();

    public ConfigurableObjectCollection(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    protected String getDisplayName() {
        return ownerDisplayName;
    }

    public void setAll(Map<String, ? extends T> tasks) {
        elements.clear();
        elements.putAll(tasks);
    }
    
    public Map<String, T> getAll() {
        return elements;
    }

    public T get(String name) {
        return elements.get(name);
    }

    public void put(String name, T child) {
        elements.put(name, child);
    }

    @Override
    public boolean hasProperty(String name) {
        return elements.containsKey(name);
    }

    @Override
    public T getProperty(String name) throws MissingPropertyException {
        if (!hasProperty(name)) {
            throw new MissingPropertyException(name, this.getClass());
        }
        return elements.get(name);
    }

    @Override
    public Map<String, T> getProperties() {
        return elements;
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        return elements.containsKey(name) && arguments.length == 1 && arguments[0] instanceof Closure;
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws MissingMethodException {
        return ConfigureUtil.configure((Closure) arguments[0], elements.get(name));
    }
}
