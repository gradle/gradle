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
 
package org.gradle.api.internal.plugins;

import org.gradle.api.plugins.Convention;
import org.gradle.api.internal.BeanDynamicObject;

import java.util.*;

import groovy.lang.MissingPropertyException;
import groovy.lang.MissingMethodException;

/**
 * @author Hans Dockter
 */
public class AbstractConvention implements Convention {

    private final Map<String, Object> plugins = new LinkedHashMap<String, Object>();

    public Map<String, Object> getPlugins() {
        return plugins;
    }

    public boolean hasProperty(String property) {
        for (Object object : plugins.values()) {
            if (new BeanDynamicObject(object).hasProperty(property)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();
        List<Object> reverseOrder = new ArrayList<Object>(plugins.values());
        Collections.reverse(reverseOrder);
        for (Object object : reverseOrder) {
            properties.putAll(new BeanDynamicObject(object).getProperties());
        }
        return properties;
    }

    public Object getProperty(String name) throws MissingPropertyException {
        BeanDynamicObject dynamicObject = new BeanDynamicObject(this);
        if (dynamicObject.hasProperty(name)) {
            return dynamicObject.getProperty(name);
        }
        for (Object object : plugins.values()) {
            dynamicObject = new BeanDynamicObject(object);
            if (dynamicObject.hasProperty(name)) {
                return dynamicObject.getProperty(name);
            }
        }
        throw new MissingPropertyException(name, Convention.class);
    }

    public void setProperty(String property, Object value) {
        for (Object object : plugins.values()) {
            BeanDynamicObject dynamicObject = new BeanDynamicObject(object);
            if (dynamicObject.hasProperty(property)) {
                dynamicObject.setProperty(property, value);
                return;
            }
        }
        throw new MissingPropertyException(property, Convention.class);
    }

    public Object invokeMethod(String name, Object... arguments) {
        for (Object object : plugins.values()) {
            BeanDynamicObject dynamicObject = new BeanDynamicObject(object);
            if (dynamicObject.hasMethod(name, arguments)) {
                return dynamicObject.invokeMethod(name, arguments);
            }
        }
        throw new MissingMethodException(name, Convention.class, arguments);
    }

    public boolean hasMethod(String method, Object... args) {
        for (Object object : plugins.values()) {
            BeanDynamicObject dynamicObject = new BeanDynamicObject(object);
            if (dynamicObject.hasMethod(method, args)) {
                return true;
            }
        }
        return false;
    }

    public <T> T getPlugin(Class<T> type) {
        T value = findPlugin(type);
        if (value == null) {
            throw new IllegalStateException(String.format("Could not find any convention object of type %s.",
                    type.getSimpleName()));
        }
        return value;
    }

    public <T> T findPlugin(Class<T> type) throws IllegalStateException {
        List<T> values = new ArrayList<T>();
        for (Object object : plugins.values()) {
            if (type.isInstance(object)) {
                values.add(type.cast(object));
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalStateException(String.format("Found multiple convention objects of type %s.",
                    type.getSimpleName()));
        }
        return values.get(0);
    }
}