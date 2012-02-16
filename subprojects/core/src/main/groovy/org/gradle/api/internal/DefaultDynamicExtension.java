/*
 * Copyright 2012 the original author or authors.
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
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import org.gradle.api.DynamicExtension;

import java.util.HashMap;
import java.util.Map;

public class DefaultDynamicExtension extends GroovyObjectSupport implements DynamicExtension{

    Map<String, Object> storage = new HashMap<String, Object>();
    
    public void add(String name, Object value) {
        storage.put(name, value);
    }

    public Object get(String name) {
        if (storage.containsKey(name)) {
            return storage.get(name);    
        } else {
            throw new UnknownPropertyException(this, name, true);
        }
    }

    public void set(String name, Object value) {
        if (storage.containsKey(name)) {
            storage.put(name, value);
        } else {
            throw new UnknownPropertyException(this, name, false);
        }
    }

    public Object getProperty(String name) {
        try {
            return get(name);
        } catch (UnknownPropertyException e) {
            throw new MissingPropertyException(e.getMessage());
        }
    }

    public void setProperty(String name, Object newValue) {
        try {
            set(name, newValue);
        } catch (UnknownPropertyException e) {
            throw new MissingPropertyException(e.getMessage());
        }
    }
    
    public Object methodMissing(String name, Object args) {
        Object item = storage.get(name);
        if (item != null && item instanceof Closure) {
            Closure closure = (Closure)item;
            return closure.call((Object[])args);
        } else {
            throw new groovy.lang.MissingMethodException(name, getClass(), (Object[])args);
        }
    }

}
