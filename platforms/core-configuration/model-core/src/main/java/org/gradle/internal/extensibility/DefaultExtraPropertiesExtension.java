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

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import groovy.lang.ReadOnlyPropertyException;
import org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultExtraPropertiesExtension extends GroovyObjectSupport implements ExtraPropertiesExtensionInternal {

    @Nullable
    private GradleProperties gradleProperties;

    @Nullable
    private Map<String, Object> storage = null;

    @Override
    public boolean has(String name) {
        if (storage != null && storage.containsKey(name)) {
            return true;
        }

        if (gradleProperties != null) {
            return gradleProperties.findUnsafe(name) != null;
        }

        return false;
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
    private Object find(String name) {
        if (storage != null) {
            Object value = storage.get(name);
            if (value != null || storage.containsKey(name)) {
                return value;
            }
        }

        if (gradleProperties != null) {
            return gradleProperties.findUnsafe(name);
        }

        return null;
    }

    @Override
    public void set(String name, @Nullable Object value) {
        if (storage == null) {
            storage = new HashMap<>();
        }
        storage.put(name, value);
    }

    @Override
    @Nullable
    public Object getProperty(String name) {
        if (name.equals("properties")) {
            return getProperties();
        }

        if (storage != null) {
            Object value = storage.get(name);
            if (value != null || storage.containsKey(name)) {
                return value;
            }
        }

        if (gradleProperties != null) {
            Object value = gradleProperties.findUnsafe(name);
            if (value != null) {
                return value;
            }
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
        // Must return a mutable map to preserve the contract
        // TODO:configuration-cache introduce a lazy mutable map that does not force eager reading of all Gradle properties
        if (storage == null) {
            return new HashMap<>(getGradlePropertiesAsMap());
        }
        Map<String, Object> gradlePropertiesMap = getGradlePropertiesAsMap();
        Map<String, Object> properties = new HashMap<>(storage.size() + gradlePropertiesMap.size());
        properties.putAll(storage);
        for (Map.Entry<String, Object> entry : gradlePropertiesMap.entrySet()) {
            if (!storage.containsKey(entry.getKey())) {
                properties.put(entry.getKey(), entry.getValue());
            }
        }
        return properties;
    }

    private Map<String, Object> getGradlePropertiesAsMap() {
        return gradleProperties == null ? Collections.emptyMap() : uncheckedNonnullCast(gradleProperties.getProperties());
    }

    @SuppressWarnings("rawtypes")
    public Object methodMissing(String name, Object args) {
        Object item = find(name);
        if (item instanceof Closure) {
            Closure closure = (Closure) item;
            return closure.call((Object[]) args);
        } else {
            throw new groovy.lang.MissingMethodException(name, getClass(), (Object[]) args);
        }
    }

    @Override
    public void setGradleProperties(GradleProperties gradleProperties) {
        this.gradleProperties = gradleProperties;
    }
}
