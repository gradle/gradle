/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal;

import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.ManifestException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DefaultAttributes implements Attributes {
    protected Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public int size() {
        return attributes.size();
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    public boolean containsKey(Object key) {
        return attributes.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return attributes.containsValue(value);
    }

    public Object get(Object key) {
        return attributes.get(key);
    }

    public Object put(String key, Object value) {
        if (key == null) {
            throw new ManifestException("The key of a manifest attribute must not be null.");
        }
        if (value == null) {
            throw new ManifestException("The value of a manifest attribute must not be null.");
        }
        try {
            new java.util.jar.Attributes.Name(key);
        } catch(IllegalArgumentException e) {
            throw new ManifestException(String.format("The Key=%s violates the Manifest spec!", key));   
        }
        return attributes.put(key, value);
    }

    public Object remove(Object key) {
        return attributes.remove(key);
    }

    public void putAll(Map<? extends String, ? extends Object> m) {
        for (Entry<? extends String, ? extends Object> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void clear() {
        attributes.clear();
    }

    public Set<String> keySet() {
        return attributes.keySet();
    }

    public Collection<Object> values() {
        return attributes.values();
    }

    public Set<Entry<String, Object>> entrySet() {
        return attributes.entrySet();
    }

    public boolean equals(Object o) {
        return attributes.equals(o);
    }

    public int hashCode() {
        return attributes.hashCode();
    }
}
