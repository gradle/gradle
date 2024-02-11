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
package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

public class FactoryNamedDomainObjectContainer<T> extends AbstractNamedDomainObjectContainer<T> {

    private final NamedDomainObjectFactory<T> factory;
    private final MutationGuard crossProjectConfiguratorMutationGuard;

    /**
     * <p>Creates a container that instantiates using the given factory.<p>
     *
     * @param type The concrete type of element in the container (must implement {@link Named})
     * @param instantiator The instantiator to use to create any other collections based on this one
     * @param factory The factory responsible for creating new instances on demand
     * @param collectionCallbackActionDecorator the decorator for collection callback action execution
     */
    public FactoryNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, NamedDomainObjectFactory<T> factory, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this(type, instantiator, Named.Namer.forType(type), factory, MutationGuards.identity(), collectionCallbackActionDecorator);
    }

    /**
     * <p>Creates a container that instantiates using the given factory.<p>
     *
     * @param type The concrete type of element in the container
     * @param instantiator The instantiator to use to create any other collections based on this one
     * @param namer The naming strategy to use
     * @param factory The factory responsible for creating new instances on demand
     */
    public FactoryNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, NamedDomainObjectFactory<T> factory, MutationGuard crossProjectConfiguratorMutationGuard, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        super(type, instantiator, namer, collectionCallbackActionDecorator);
        this.factory = factory;
        this.crossProjectConfiguratorMutationGuard = crossProjectConfiguratorMutationGuard;
    }

    /**
     * <p>Creates a container that instantiates using the given factory.<p>
     *
     * @param type The concrete type of element in the container (must implement {@link Named})
     * @param instantiator The instantiator to use to create any other collections based on this one
     * @param factoryClosure The closure responsible for creating new instances on demand
     */
    public FactoryNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, final Closure<?> factoryClosure, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this(type, instantiator, Named.Namer.forType(type), factoryClosure, MutationGuards.identity(), collectionCallbackActionDecorator);
    }

    /**
     * <p>Creates a container that instantiates using the given factory.<p>
     *
     * @param type The concrete type of element in the container
     * @param instantiator The instantiator to use to create any other collections based on this one
     * @param namer The naming strategy to use
     * @param factoryClosure The factory responsible for creating new instances on demand
     */
    public FactoryNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, final Closure<?> factoryClosure, MutationGuard mutationGuard, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this(type, instantiator, namer, new ClosureObjectFactory<>(type, factoryClosure), mutationGuard, collectionCallbackActionDecorator);
    }

    @Override
    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(CollectionFilter<S> filter) {
        return Cast.uncheckedNonnullCast(getInstantiator().newInstance(DefaultNamedDomainObjectSet.class, this, filter, getInstantiator(), getNamer(), crossProjectConfiguratorMutationGuard));
    }

    @Override
    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(Spec<String> nameFilter, CollectionFilter<S> elementFilter) {
        return Cast.uncheckedNonnullCast(getInstantiator().newInstance(DefaultNamedDomainObjectSet.class, this, nameFilter, elementFilter, getInstantiator(), getNamer(), crossProjectConfiguratorMutationGuard));
    }

    @Override
    protected <I extends T> Action<? super I> withMutationDisabled(Action<? super I> action) {
        return crossProjectConfiguratorMutationGuard.withMutationDisabled(super.withMutationDisabled(action));
    }

    @Override
    protected T doCreate(String name) {
        return factory.create(name);
    }

    private static class ClosureObjectFactory<T> implements NamedDomainObjectFactory<T> {
        private final Class<T> type;
        private final Closure<?> factoryClosure;

        public ClosureObjectFactory(Class<T> type, Closure<?> factoryClosure) {
            this.type = type;
            this.factoryClosure = factoryClosure;
        }

        @Override
        public T create(String name) {
            return type.cast(factoryClosure.call(name));
        }
    }
}
