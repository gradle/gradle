/*
 * Copyright 2011 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.util.ConfigureUtil;

import java.util.*;

/**
 * @author: Szczepan Faber, created at: 6/24/11
 */
public class ExtensionsStorage {

    private final Map<String, Object> extensions = new LinkedHashMap<String, Object>();

    public void add(String name, Object extension) {
        if (extensions.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Cannot add extension with name '%s', as there is an extension already registered with that name.", name));
        }
        extensions.put(name, extension);
    }

    public boolean hasExtension(String name) {
        return extensions.containsKey(name);
    }

    public Map<String, Object> getAsMap() {
        return extensions;
    }

    public void checkExtensionIsNotReassigned(String name) {
        if (hasExtension(name)) {
            throw new IllegalArgumentException(String.format("There's an extension registered with name '%s'. You should not reassign it via a property setter.", name));
        }
    }

    public boolean isConfigureExtensionMethod(String methodName, Object ... arguments) {
        return extensions.containsKey(methodName) && arguments.length == 1 && arguments[0] instanceof Closure;
    }

    public Object configureExtension(String methodName, Object ... arguments) {
        return ConfigureUtil.configure((Closure) arguments[0], extensions.get(methodName));
    }

    public <T> T getByType(Class<T> type) {
        Collection<Object> values = extensions.values();
        List types = new LinkedList();
        for (Object e : values) {
            Class clazz = e.getClass();
            types.add(clazz.getSimpleName());
            if (type.isAssignableFrom(clazz)) {
                return (T) e;
            }
        }
        throw new UnknownDomainObjectException("Extension of type '" + type.getSimpleName() + "' does not exist. Currently registered extension types: " + types);
    }

    public <T> T findByType(Class<T> type) {
        Collection<Object> values = extensions.values();
        for (Object e : values) {
            if (type.isAssignableFrom(e.getClass())) {
                return (T) e;
            }
        }
        return null;
    }

    public Object getByName(String name) {
        if (!hasExtension(name)) {
            throw new UnknownDomainObjectException("Extension with name '" + name + "' does not exist. Currently registered extension names: " + extensions.keySet());
        }
        return extensions.get(name);
    }

    public Object findByName(String name) {
        return extensions.get(name);
    }
}