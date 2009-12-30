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

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

public abstract class AbstractNamedDomainObjectCollection<T> extends AbstractDomainObjectCollection<T> implements NamedDomainObjectCollection<T> {
    private final DynamicObject dynamicObject = new ContainerDynamicObject();

    protected AbstractNamedDomainObjectCollection(Store<T> store) {
        super(store);
    }

    /**
     * Returns the display name of this collection
     *
     * @return The display name
     */
    public abstract String getDisplayName();

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

    /**
     * Returns a {@link DynamicObject} which can be used to access the domain objects as dynamic properties and
     * methods.
     *
     * @return The dynamic object
     */
    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }

    /**
     * Called when an unknown domain object is requested.
     *
     * @param name The name of the unknown object
     * @return The exception to throw.
     */
    protected abstract UnknownDomainObjectException createNotFoundException(String name);

    private class ContainerDynamicObject extends CompositeDynamicObject {
        private ContainerDynamicObject() {
            setObjects(new BeanDynamicObject(AbstractNamedDomainObjectCollection.this), new ContainerElementsDynamicObject());
        }

        @Override
        protected String getDisplayName() {
            return AbstractNamedDomainObjectCollection.this.getDisplayName();
        }
    }

    private class ContainerElementsDynamicObject extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return AbstractNamedDomainObjectCollection.this.getDisplayName();
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
