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
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.Transformers;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class AbstractPolymorphicDomainObjectContainer<T>
        extends AbstractNamedDomainObjectContainer<T> implements PolymorphicDomainObjectContainerInternal<T> {

    private final ContainerElementsDynamicObject elementsDynamicObject = new ContainerElementsDynamicObject();

    protected AbstractPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
        super(type, instantiator, namer);
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
    public <U extends T> Provider<U> register(String name, Class<U> type) throws InvalidUserDataException {
        return createDomainObjectProvider(name, type, null);
    }

    @Override
    public <U extends T> Provider<U> register(String name, Class<U> type, Action<? super U> configurationAction) throws InvalidUserDataException {
        return createDomainObjectProvider(name, type, configurationAction);
    }

    protected <U extends T> Provider<U> createDomainObjectProvider(String name, Class<U> type, @Nullable Action<? super U> configurationAction) {
        assertCanAdd(name);
        Provider<U> provider = Cast.uncheckedCast(
            getInstantiator().newInstance(DomainObjectCreatingProvider.class, AbstractPolymorphicDomainObjectContainer.this, name, type, configurationAction)
        );
        addLater(provider);
        return provider;
    }

    // Cannot be private due to reflective instantiation
    public class DomainObjectCreatingProvider<I extends T> extends AbstractDomainObjectProvider<I> {
        private I object;
        private Throwable cause;
        private ImmutableActionSet<I> onCreate;
        private final Class<I> type;

        public DomainObjectCreatingProvider(String name, Class<I> type, @Nullable Action<? super I> configureAction) {
            super(name);
            this.type = type;
            this.onCreate = ImmutableActionSet.<I>empty().mergeFrom(getEventRegister().getAddActions());

            if (configureAction != null) {
                configure(configureAction);
            }
        }

        @Override
        public boolean isPresent() {
            return findDomainObject(getName()) != null;
        }

        public void configure(final Action<? super I> action) {
            if (object != null) {
                // Already realized, just run the action now
                action.execute(object);
                return;
            }
            // Collect any container level add actions then add the object specific action
            onCreate = onCreate.mergeFrom(getEventRegister().getAddActions()).add(action);
        }

        @Override
        public I getOrNull() {
            if (cause != null) {
                throw createIllegalStateException();
            }
            if (object == null) {
                object = getType().cast(findByNameWithoutRules(getName()));
                if (object == null) {
                    try {
                        // Collect any container level add actions added since the last call to configure()
                        onCreate = onCreate.mergeFrom(getEventRegister().getAddActions());

                        // Create the domain object
                        object = doCreate(getName(), type);

                        // Register the domain object
                        add(object, onCreate);
                    } catch (RuntimeException ex) {
                        cause = ex;
                        throw createIllegalStateException();
                    } finally {
                        // Discard state that is no longer required
                        onCreate = ImmutableActionSet.empty();
                    }
                }
            }
            return object;
        }

        private IllegalStateException createIllegalStateException() {
            return new IllegalStateException(String.format("Could not create domain object '%s' (%s)", getName(), getType().getSimpleName()), cause);
        }
    }

    @Override
    protected DynamicObject getElementsAsDynamicObject() {
        return elementsDynamicObject;
    }

    @Override
    protected ConfigureDelegate createConfigureDelegate(Closure configureClosure) {
        return new PolymorphicDomainObjectContainerConfigureDelegate(configureClosure, this);
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
        public boolean hasMethod(String name, Object... arguments) {
            return isConfigureMethod(name, arguments);
        }

        @Override
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
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

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure
                    || arguments.length == 1 && arguments[0] instanceof Class
                    || arguments.length == 2 && arguments[0] instanceof Class && arguments[1] instanceof Closure)
                    && hasProperty(name);
        }
    }

    public <U extends T> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        return getInstantiator().newInstance(TypedDomainObjectContainerWrapper.class, type, this);
    }

}
