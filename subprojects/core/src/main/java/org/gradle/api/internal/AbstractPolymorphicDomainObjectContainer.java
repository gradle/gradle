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
package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Namer;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.metaobject.MixInClosurePropertiesAsMethodsDynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

public abstract class AbstractPolymorphicDomainObjectContainer<T>
        extends AbstractNamedDomainObjectContainer<T> implements PolymorphicDomainObjectContainerInternal<T> {

    private final ContainerElementsDynamicObject elementsDynamicObject = new ContainerElementsDynamicObject();
    private final DynamicObject dynamicObject;

    protected AbstractPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
        super(type, instantiator, namer);
        this.dynamicObject = new ExtensibleDynamicObject(this, new ContainerDynamicObject(elementsDynamicObject), getConvention());
    }

    protected abstract <U extends T> U doCreate(String name, Class<U> type);

    public <U extends T> U create(String name, Class<U> type) {
        return create(name, type, null);
    }

    public <U extends T> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        T item = findByName(name);
        if (item != null) {
            return Transformers.cast(type).transform(item);
        }
        return create(name, type);
    }

    public <U extends T> U create(String name, Class<U> type, Action<? super U> configuration) {
        assertCanAdd(name);
        U object = doCreate(name, type);
        add(object);
        if (configuration != null) {
            configuration.execute(object);
        }
        return object;
    }

    @Override
    protected DynamicObject getElementsAsDynamicObject() {
        return elementsDynamicObject;
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }

    @Override
    protected ConfigureDelegate createConfigureDelegate(Closure configureClosure) {
        return new PolymorphicDomainObjectContainerConfigureDelegate(configureClosure, this);
    }

    private class ContainerDynamicObject extends MixInClosurePropertiesAsMethodsDynamicObject {
        private ContainerDynamicObject(ContainerElementsDynamicObject elementsDynamicObject) {
            setObjects(new BeanDynamicObject(AbstractPolymorphicDomainObjectContainer.this), elementsDynamicObject, getConvention().getExtensionsAsDynamicObject());
        }

        @Override
        public String getDisplayName() {
            return AbstractPolymorphicDomainObjectContainer.this.getDisplayName();
        }
    }

    private class ContainerElementsDynamicObject extends AbstractDynamicObject {
        @Override
        public String getDisplayName() {
            return AbstractPolymorphicDomainObjectContainer.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return findByName(name) != null;
        }

        @Override
        public void getProperty(String name, GetPropertyResult result) {
            Object object = findByName(name);
            if (object != null) {
                result.result(object);
            }
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
        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            if (isConfigureMethod(name, arguments)) {
                T element = getByName(name);
                Object lastArgument = arguments[arguments.length - 1];
                if (lastArgument instanceof Closure) {
                    ConfigureUtil.configure((Closure) lastArgument, element);
                }
                result.result(element);
            }
        }

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure
                    || arguments.length == 1 && arguments[0] instanceof Class
                    || arguments.length == 2 && arguments[0] instanceof Class && arguments[1] instanceof Closure)
                    && hasProperty(name);
        }
    }

    public <U extends T> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        return getInstantiator().newInstance(TypedDomainObjectContainerWrapper.class, type, this, getInstantiator());
    }

}
