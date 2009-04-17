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

import groovy.lang.*;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.ConfigureUtil;

import java.util.*;

public class DefaultDomainObjectContainer<T> implements DomainObjectContainer<T> {
    private final Map<String, T> objects = new TreeMap<String, T>();

    /**
     * Adds a domain object to this container.
     *
     * @param name The name of the domain object.
     * @param object The object to add
     */
    protected void add(String name, T object) {
        objects.put(name, object);
    }

    /**
     * Returns the display name of this container
     *
     * @return The display name
     */
    public String getDisplayName() {
        return "domain object container";
    }

    /**
     * Returns a {@link DynamicObject} which can be used to access the domain objects as dynamic properties and methods.
     *
     * @return The dynamic object
     */
    public DynamicObject getAsDynamicObject() {
        return new ContainerDynamicObject();
    }

    public Set<T> getAll() {
        return new LinkedHashSet<T>(objects.values());
    }

    public Set<T> get(Spec<? super T> spec) {
        return Specs.filterIterable(objects.values(), spec);
    }

    public Map<String, T> getAsMap() {
        return Collections.unmodifiableMap(objects);
    }

    public T find(String name) {
        return objects.get(name);
    }

    public T get(String name) throws UnknownDomainObjectException {
        T t = find(name);
        if (t == null) {
            throw createNotFoundException(name);
        }
        return t;
    }

    public T get(String name, Closure configureClosure) throws UnknownDomainObjectException {
        T t = get(name);
        ConfigureUtil.configure(configureClosure, t);
        return t;
    }

    /**
     * Called when an unknown domain object is requested.
     *
     * @param name The name of the unknonw object
     * @return The exception to throw.
     */
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownDomainObjectException(String.format("Domain object with name '%s' not found.", name));
    }

    private class ContainerDynamicObject extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return DefaultDomainObjectContainer.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return objects.containsKey(name);
        }

        @Override
        public T getProperty(String name) throws MissingPropertyException {
            if (!hasProperty(name)) {
                throw new MissingPropertyException(name, this.getClass());
            }
            return objects.get(name);
        }

        @Override
        public Map<String, T> getProperties() {
            return objects;
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return objects.containsKey(name) && arguments.length == 1 && arguments[0] instanceof Closure;
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
            return ConfigureUtil.configure((Closure) arguments[0], objects.get(name));
        }
    }
}
