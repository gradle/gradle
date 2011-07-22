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
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;
import org.gradle.api.Namer;

public abstract class AbstractAutoCreateDomainObjectContainer<T> extends DefaultNamedDomainObjectSet<T> implements
        Configurable<AbstractAutoCreateDomainObjectContainer<T>> {
    protected AbstractAutoCreateDomainObjectContainer(Class<T> type, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, classGenerator, namer);
    }

    protected abstract T doCreate(String name);

    public T create(String name) {
        return create(name, null);
    }

    public T create(String name, Closure configureClosure) {
        if (findByName(name) != null) {
            throw new InvalidUserDataException(String.format("Cannot add %s '%s' as a %s with that name already exists.",
                    getTypeDisplayName(), name, getTypeDisplayName()));
        }
        T object = doCreate(name);
        add(object);
        ConfigureUtil.configure(configureClosure, object);
        return object;
    }

    public AbstractAutoCreateDomainObjectContainer<T> configure(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, new AutoCreateDomainObjectContainerDelegate(
                configureClosure.getOwner(), this));
        return this;
    }
}
