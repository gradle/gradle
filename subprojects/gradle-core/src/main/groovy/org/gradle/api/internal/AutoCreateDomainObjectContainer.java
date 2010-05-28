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

public abstract class AutoCreateDomainObjectContainer<T> extends DefaultNamedDomainObjectContainer<T> implements
        Configurable<AutoCreateDomainObjectContainer<T>> {
    protected AutoCreateDomainObjectContainer(Class<T> type, ClassGenerator classGenerator) {
        super(type, classGenerator);
    }

    protected abstract T create(String name);

    public T add(String name) {
        return add(name, null);
    }

    public T add(String name, Closure configureClosure) {
        if (findByName(name) != null) {
            throw new InvalidUserDataException(String.format("Cannot add %s '%s' as a %s with that name already exists.",
                    getTypeDisplayName(), name, getTypeDisplayName()));
        }
        T object = create(name);
        addObject(name, object);
        ConfigureUtil.configure(configureClosure, object);
        return object;
    }

    public AutoCreateDomainObjectContainer<T> configure(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, new AutoCreateDomainObjectContainerDelegate(
                configureClosure.getOwner(), this));
        return this;
    }
}
