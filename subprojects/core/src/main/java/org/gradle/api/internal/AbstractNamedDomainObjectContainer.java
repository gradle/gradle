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
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Namer;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;

import static org.gradle.api.reflect.TypeOf.parameterizedTypeOf;
import static org.gradle.api.reflect.TypeOf.typeOf;

public abstract class AbstractNamedDomainObjectContainer<T> extends DefaultNamedDomainObjectSet<T> implements NamedDomainObjectContainer<T>, HasPublicType {

    protected AbstractNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
        super(type, instantiator, namer);
    }

    protected AbstractNamedDomainObjectContainer(Class<T> type, Instantiator instantiator) {
        super(type, instantiator, Named.Namer.forType(type));
    }

    /**
     * Subclasses need only implement this method as the creation strategy.
     */
    protected abstract T doCreate(String name);

    public T create(String name) {
        return create(name, Actions.doNothing());
    }

    public T maybeCreate(String name) {
        T item = findByName(name);
        if (item != null) {
            return item;
        }
        return create(name);
    }

    public T create(String name, Closure configureClosure) {
        return create(name, ConfigureUtil.configureUsing(configureClosure));
    }

    public T create(String name, Action<? super T> configureAction) throws InvalidUserDataException {
        assertCanAdd(name);
        T object = doCreate(name);
        add(object);
        configureAction.execute(object);
        return object;
    }

    protected ConfigureDelegate createConfigureDelegate(Closure configureClosure) {
        return new NamedDomainObjectContainerConfigureDelegate(configureClosure, this);
    }

    public AbstractNamedDomainObjectContainer<T> configure(Closure configureClosure) {
        ConfigureDelegate delegate = createConfigureDelegate(configureClosure);
        ConfigureUtil.configureSelf(configureClosure, this, delegate);
        return this;
    }

    public String getDisplayName() {
        return getTypeDisplayName() + " container";
    }

    @Override
    public TypeOf<?> getPublicType() {
        return parameterizedTypeOf(new TypeOf<NamedDomainObjectContainer<?>>() {}, typeOf(getType()));
    }

    @Override
    public Provider<T> register(String name) throws InvalidUserDataException {
        return createDomainObjectProvider(name, null);
    }

    @Override
    public Provider<T> register(String name, Action<? super T> configurationAction) throws InvalidUserDataException {
        return createDomainObjectProvider(name, configurationAction);
    }

    protected Provider<T> createDomainObjectProvider(String name, @Nullable Action<? super T> configurationAction) {
        assertCanAdd(name);
        Provider<T> provider = Cast.uncheckedCast(
            getInstantiator().newInstance(DomainObjectCreatingProvider.class, AbstractNamedDomainObjectContainer.this, name, configurationAction)
        );
        addLater(provider);
        return provider;
    }

    // Cannot be private due to reflective instantiation
    public class DomainObjectCreatingProvider<I extends T> extends AbstractDomainObjectProvider<I> {
        private I object;
        private Throwable cause;
        private ImmutableActionSet<I> onCreate;

        public DomainObjectCreatingProvider(String name, @Nullable Action<? super I> configureAction) {
            super(name);
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
                        object = (I) doCreate(getName());

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
}
