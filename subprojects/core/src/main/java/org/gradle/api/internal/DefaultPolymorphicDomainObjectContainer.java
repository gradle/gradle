/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.internal.Cast;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.NamedEntityInstantiator;

import java.util.Set;

public class DefaultPolymorphicDomainObjectContainer<T> extends AbstractPolymorphicDomainObjectContainer<T>
    implements ExtensiblePolymorphicDomainObjectContainer<T> {
    protected final DefaultPolymorphicNamedEntityInstantiator<T> namedEntityInstantiator;

    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator callbackDecorator) {
        super(type, instantiator, namer, callbackDecorator);
        namedEntityInstantiator = new DefaultPolymorphicNamedEntityInstantiator<T>(type, "this container");
    }

    /**
     * This internal constructor is used by the 'idea-ext' plugin which we use in our build.
     */
    @Deprecated
    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator) {
        this(type, instantiator, Named.Namer.forType(type), CollectionCallbackActionDecorator.NOOP);
        DeprecationLogger.deprecateInternalApi("constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator)")
            .replaceWith("ObjectFactory.polymorphicDomainObjectContainer(Class<T>)")
            .willBeRemovedInGradle7()
            .withUserManual("custom_gradle_types", "extensiblepolymorphicdomainobjectcontainer")
            .nagUser();
    }

    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, CollectionCallbackActionDecorator callbackDecorator) {
        this(type, instantiator, Named.Namer.forType(type), callbackDecorator);
    }

    @Override
    public NamedEntityInstantiator<T> getEntityInstantiator() {
        return namedEntityInstantiator;
    }

    @Override
    protected T doCreate(String name) {
        try {
            return namedEntityInstantiator.create(name, getType());
        } catch (InvalidUserDataException e) {
            if (e.getCause() instanceof NoFactoryRegisteredForTypeException) {
                throw new InvalidUserDataException(String.format("Cannot create a %s named '%s' because this container "
                    + "does not support creating elements by name alone. Please specify which subtype of %s to create. "
                    + "Known subtypes are: %s", getTypeDisplayName(), name, getTypeDisplayName(), namedEntityInstantiator.getSupportedTypeNames()));
            } else {
                throw e;
            }
        }
    }

    @Override
    protected <U extends T> U doCreate(String name, Class<U> type) {
        return namedEntityInstantiator.create(name, type);
    }

    public <U extends T> void registerDefaultFactory(NamedDomainObjectFactory<U> factory) {
        Class<T> castType = Cast.uncheckedCast(getType());
        registerFactory(castType, factory);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        namedEntityInstantiator.registerFactory(type, factory);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, final Closure<? extends U> factory) {
        registerFactory(type, new NamedDomainObjectFactory<U>() {
            @Override
            public U create(String name) {
                return factory.call(name);
            }
        });
    }

    @Override
    public <U extends T> void registerBinding(Class<U> type, final Class<? extends U> implementationType) {
        registerFactory(type, new NamedDomainObjectFactory<U>() {
            boolean named = Named.class.isAssignableFrom(implementationType);

            @Override
            public U create(String name) {
                return named ? getInstantiator().newInstance(implementationType, name)
                    : getInstantiator().newInstance(implementationType);
            }
        });
    }

    @Override
    public Set<? extends Class<? extends T>> getCreateableTypes() {
        return namedEntityInstantiator.getCreatableTypes();
    }
}
