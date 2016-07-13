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
import org.gradle.internal.Actions;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

public abstract class AbstractNamedDomainObjectContainer<T> extends DefaultNamedDomainObjectSet<T> implements NamedDomainObjectContainer<T> {

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

}
