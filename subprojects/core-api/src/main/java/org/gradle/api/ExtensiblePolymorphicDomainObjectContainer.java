/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api;

import groovy.lang.Closure;
import org.gradle.api.internal.rules.NamedDomainObjectFactoryRegistry;

/**
 * A {@link org.gradle.api.PolymorphicDomainObjectContainer} that can be extended at runtime to
 * create elements of new types.
 *
 * <p>You can create an instance of this type using the factory method {@link org.gradle.api.model.ObjectFactory#polymorphicDomainObjectContainer(Class)}.</p>
 *
 * @param <T> the (base) container element type
 */
public interface ExtensiblePolymorphicDomainObjectContainer<T> extends PolymorphicDomainObjectContainer<T>, NamedDomainObjectFactoryRegistry<T> {
    /**
     * Registers a factory for creating elements of the specified type. Typically, the specified type
     * is an interface type.
     *
     * @param type the type of objects created by the factory
     * @param factory the factory to register
     * @param <U> the type of objects created by the factory
     *
     * @throws IllegalArgumentException if the specified type is not a subtype of the container element type
     */
    @Override
    <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory);

    /**
     * Registers a factory for creating elements of the specified type.
     * Typically, the specified type is an interface type.
     *
     * @param type the type of objects created by the factory
     * @param factory the factory to register
     * @param <U> the type of objects created by the factory
     *
     * @throws IllegalArgumentException if the specified type is not a subtype of the container element type
     */
    <U extends T> void registerFactory(Class<U> type, final Closure<? extends U> factory);

    /**
     * Registers a binding from the specified "public" domain object type to the specified implementation type.
     * Whenever the container is asked to create an element with the binding's public type, it will instantiate
     * the binding's implementation type. If the implementation type has a constructor annotated with
     * {@link javax.inject.Inject}, its arguments will be injected.
     *
     * <p>In general, registering a binding is preferable over implementing and registering a factory.
     *
     * @param type a public domain object type
     * @param implementationType the corresponding implementation type
     * @param <U> a public domain object type
     */
    <U extends T> void registerBinding(Class<U> type, final Class<? extends U> implementationType);
}
