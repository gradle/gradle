/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import groovy.lang.ReadOnlyPropertyException;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class DefaultExtraPropertiesExtension extends GroovyObjectSupport implements ExtraPropertiesExtension {

    private final Map<String, Object> storage = new HashMap<>();

    private ImmutableMap<String, Object> gradleProperties = ImmutableMap.of();

    @Override
    public boolean has(String name) {
        if (storage.containsKey(name)) {
            return true;
        }
        onGradlePropertyLookup(name);
        return gradleProperties.containsKey(name);
    }

    @Override
    @Nullable
    public Object get(String name) {
        Object value = find(name);
        if (value == null && !has(name)) {
            throw new UnknownPropertyException(this, name);
        }
        return value;
    }

    @Nullable
    public Object find(String name) {
        if (storage.containsKey(name)) {
            return storage.get(name);
        }
        onGradlePropertyLookup(name);
        return gradleProperties.get(name);
    }

    @Override
    public void set(String name, @Nullable Object value) {
        storage.put(name, value);
    }

    @Override
    @Nullable
    public Object getProperty(String name) {
        if (name.equals("properties")) {
            return getProperties();
        }

        if (storage.containsKey(name)) {
            return storage.get(name);
        }

        onGradlePropertyLookup(name);
        if (gradleProperties.containsKey(name)) {
            return gradleProperties.get(name);
        }

        throw new MissingPropertyException(UnknownPropertyException.createMessage(name), name, null);
    }

    @Override
    public void setProperty(String name, @Nullable Object newValue) {
        if (name.equals("properties")) {
            throw new ReadOnlyPropertyException("name", ExtraPropertiesExtension.class);
        }
        set(name, newValue);
    }

    @Override
    public Map<String, Object> getProperties() {
        // TODO:configuration-cache use a tracking map here
        HashMap<String, Object> properties = new HashMap<>(storage);
        for (Map.Entry<String, Object> entry : gradleProperties.entrySet()) {
            if (!properties.containsKey(entry.getKey())) {
                onGradlePropertyLookup(entry.getKey());
                properties.put(entry.getKey(), entry.getValue());
            }
        }
        return properties;
    }

    public Object methodMissing(String name, Object args) {
        Object item = find(name);
        if (item instanceof Closure) {
            Closure closure = (Closure) item;
            return closure.call((Object[]) args);
        } else {
            throw new groovy.lang.MissingMethodException(name, getClass(), (Object[]) args);
        }
    }

    public void setGradleProperties(Map<String, Object> properties) {
        gradleProperties = ImmutableMap.copyOf(properties);
    }

    private void onGradlePropertyLookup(@SuppressWarnings("unused") String name) {
        // TODO:configuration-cache track property usage
    }
}
