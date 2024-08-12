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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Namer;
import org.gradle.internal.Cast;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class AbstractPolymorphicDomainObjectContainer<T>
        extends AbstractNamedDomainObjectContainer<T> implements PolymorphicDomainObjectContainerInternal<T> {

    private final ContainerElementsDynamicObject elementsDynamicObject = new ContainerElementsDynamicObject();

    protected AbstractPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator callbackDecorator) {
        super(type, instantiator, namer, callbackDecorator);
    }

    protected abstract <U extends T> U doCreate(String name, Class<U> type);

    @Override
    public <U extends T> U create(String name, Class<U> type) {
        assertCanMutate("create(String, Class)");
        return create(name, type, null);
    }

    @Override
    public <U extends T> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        T item = findByName(name);
        if (item != null) {
            return Transformers.cast(type).transform(item);
        }
        return create(name, type);
    }

    @Override
    public <U extends T> U create(String name, Class<U> type, Action<? super U> configuration) {
        assertCanMutate("create(String, Class, Action)");
        assertElementNotPresent(name);
        U object = doCreate(name, type);
        add(object);
        if (configuration != null) {
            configuration.execute(object);
        }
        return object;
    }

    @Override
    public <U extends T> NamedDomainObjectProvider<U> register(String name, Class<U> type) throws InvalidUserDataException {
        assertCanMutate("register(String, Class)");
        return createDomainObjectProvider(name, type, null);
    }

    @Override
    public <U extends T> NamedDomainObjectProvider<U> register(String name, Class<U> type, Action<? super U> configurationAction) throws InvalidUserDataException {
        assertCanMutate("register(String, Class, Action)");
        return createDomainObjectProvider(name, type, configurationAction);
    }

    protected <U extends T> NamedDomainObjectProvider<U> createDomainObjectProvider(String name, Class<U> type, @Nullable Action<? super U> configurationAction) {
        assertElementNotPresent(name);
        NamedDomainObjectProvider<U> provider = Cast.uncheckedCast(
            getInstantiator().newInstance(NamedDomainObjectCreatingProvider.class, AbstractPolymorphicDomainObjectContainer.this, name, type, configurationAction)
        );
        addLater(provider);
        return provider;
    }

    // Cannot be private due to reflective instantiation
    public class NamedDomainObjectCreatingProvider<I extends T> extends AbstractDomainObjectCreatingProvider<I> {
        public NamedDomainObjectCreatingProvider(String name, Class<I> type, @Nullable Action<? super I> configureAction) {
            super(name, type, configureAction);
        }

        @Override
        protected I createDomainObject() {
            return doCreate(getName(), getType());
        }
    }

    @Override
    protected DynamicObject getElementsAsDynamicObject() {
        return elementsDynamicObject;
    }

    @Override
    protected ConfigureDelegate createConfigureDelegate(Closure configureClosure) {
        return new PolymorphicDomainObjectContainerConfigureDelegate<>(configureClosure, this);
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
        public DynamicInvokeResult tryGetProperty(String name) {
            Object object = findByName(name);
            return object == null ? DynamicInvokeResult.notFound() : DynamicInvokeResult.found(object);
        }

        @Override
        public Map<String, T> getProperties() {
            return getAsMap();
        }

        @Override
        public boolean hasMethod(String name, @Nullable Object... arguments) {
            return isConfigureMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
            if (isConfigureMethod(name, arguments)) {
                T element = getByName(name);
                Object lastArgument = arguments[arguments.length - 1];
                if (lastArgument instanceof Closure) {
                    ConfigureUtil.configure((Closure) lastArgument, element);
                }
                return DynamicInvokeResult.found(element);
            }
            return DynamicInvokeResult.notFound();
        }

        private boolean isConfigureMethod(String name, @Nullable Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure
                    || arguments.length == 1 && arguments[0] instanceof Class
                    || arguments.length == 2 && arguments[0] instanceof Class && arguments[1] instanceof Closure)
                    && hasProperty(name);
        }
    }

    @Override
    public <U extends T> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        return Cast.uncheckedNonnullCast(getInstantiator().newInstance(TypedDomainObjectContainerWrapper.class, type, this));
    }

}
