/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;
import org.gradle.util.internal.ConfigureUtil;

public class TypedDomainObjectContainerWrapper<U> extends DelegatingNamedDomainObjectSet<U> implements NamedDomainObjectContainer<U>, MethodMixIn, PropertyMixIn {
    private final Class<U> type;
    private final AbstractPolymorphicDomainObjectContainer<? super U> parent;

    public TypedDomainObjectContainerWrapper(Class<U> type, AbstractPolymorphicDomainObjectContainer<? super U> parent) {
        super(parent.withType(type));
        this.parent = parent;
        this.type = type;
    }

    @Override
    public U create(String name) throws InvalidUserDataException {
        return parent.create(name, type);
    }

    @Override
    public U create(String name, Action<? super U> configureAction) throws InvalidUserDataException {
        return parent.create(name, type, configureAction);
    }

    @Override
    public U create(String name, Closure configureClosure) throws InvalidUserDataException {
        return parent.create(name, type, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public U maybeCreate(String name) {
        return parent.maybeCreate(name, type);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return parent.getAdditionalMethods();
    }

    @Override
    public PropertyAccess getAdditionalProperties() {
        return parent.getAdditionalProperties();
    }

    @Override
    public NamedDomainObjectContainer<U> configure(Closure configureClosure) {
        NamedDomainObjectContainerConfigureDelegate delegate = new NamedDomainObjectContainerConfigureDelegate(configureClosure, this);
        return ConfigureUtil.configureSelf(configureClosure, this, delegate);
    }

    @Override
    public NamedDomainObjectProvider<U> register(String name, Action<? super U> configurationAction) throws InvalidUserDataException {
        return parent.register(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectProvider<U> register(String name) throws InvalidUserDataException {
        return parent.register(name, type);
    }

}
