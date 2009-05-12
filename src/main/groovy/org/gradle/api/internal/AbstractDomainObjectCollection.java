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

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.Action;
import org.gradle.util.ConfigureUtil;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.Map;

import groovy.lang.MissingPropertyException;
import groovy.lang.Closure;

public abstract class AbstractDomainObjectCollection<T> implements DomainObjectCollection<T> {
    public Set<T> getAll() {
        return new LinkedHashSet<T>(getAsMap().values());
    }

    public Set<T> findAll(Spec<? super T> spec) {
        return Specs.filterIterable(getAsMap().values(), spec);
    }

    public T getByName(String name) throws UnknownDomainObjectException {
        T t = findByName(name);
        if (t == null) {
            throw createNotFoundException(name);
        }
        return t;
    }

    public T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        T t = getByName(name);
        ConfigureUtil.configure(configureClosure, t);
        return t;
    }
    
    public T getAt(String name) throws UnknownDomainObjectException {
        return getByName(name);
    }

    public Iterator<T> iterator() {
        return getAsMap().values().iterator();
    }

    public void allObjects(Action<? super T> action) {
        for (T t : getAsMap().values()) {
            action.execute(t);
        }
        whenObjectAdded(action);
    }

    public void allObjects(Closure action) {
        for (T t : getAsMap().values()) {
            action.call(t);
        }
        whenObjectAdded(action);
    }

    /**
     * Returns a {@link DynamicObject} which can be used to access the domain objects as dynamic properties and
     * methods.
     *
     * @return The dynamic object
     */
    public DynamicObject getAsDynamicObject() {
        return new ContainerDynamicObject();
    }

    protected Object propertyMissing(String name) {
        return getAsDynamicObject().getProperty(name);
    }

    protected Object methodMissing(String name, Object args) {
        return getAsDynamicObject().invokeMethod(name, (Object[]) args);
    }

    protected abstract UnknownDomainObjectException createNotFoundException(String name);

    /**
     * Returns the display name of this collection
     *
     * @return The display name
     */
    public abstract String getDisplayName();

    private class ContainerDynamicObject extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return AbstractDomainObjectCollection.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return findByName(name) != null;
        }

        @Override
        public T getProperty(String name) throws MissingPropertyException {
            T t = findByName(name);
            if (t == null) {
                return (T) super.getProperty(name);
            }
            return t;
        }

        @Override
        public Map<String, T> getProperties() {
            return getAsMap();
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return isConfigureMethod(name, arguments);
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
            if (isConfigureMethod(name, arguments)) {
                return ConfigureUtil.configure((Closure) arguments[0], getByName(name));
            } else {
                return super.invokeMethod(name, arguments);
            }
        }

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure) && hasProperty(name);
        }
    }

}
