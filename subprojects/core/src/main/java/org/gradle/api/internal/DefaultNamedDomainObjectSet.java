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
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.SortedSetElementSource;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultNamedDomainObjectSet<T> extends DefaultNamedDomainObjectCollection<T> implements NamedDomainObjectSet<T> {
    private final MutationGuard parentMutationGuard;

    public DefaultNamedDomainObjectSet(Class<? extends T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator decorator) {
        super(type, new SortedSetElementSource<T>(new Namer.Comparator<T>(namer)), instantiator, namer, decorator);
        this.parentMutationGuard = MutationGuards.identity();
    }

    public DefaultNamedDomainObjectSet(Class<? extends T> type, Instantiator instantiator, CollectionCallbackActionDecorator decorator) {
        this(type, instantiator, Named.Namer.forType(type), decorator);
    }

    // should be protected, but use of the class generator forces it to be public
    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, Namer<? super T> namer) {
        this(collection, filter, instantiator, namer, MutationGuards.identity());
    }

    // should be protected, but use of the class generator forces it to be public
    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> objects, Spec<String> nameFilter, CollectionFilter<T> elementFilter, Instantiator instantiator, Namer<? super T> namer) {
        this(objects, nameFilter, elementFilter, instantiator, namer, MutationGuards.identity());
    }

    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, Namer<? super T> namer, MutationGuard parentMutationGuard) {
        this(collection, Specs.satisfyAll(), filter, instantiator, namer, parentMutationGuard);
    }

    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> collection, Spec<String> nameFilter, CollectionFilter<T> elementFilter, Instantiator instantiator, Namer<? super T> namer, MutationGuard parentMutationGuard) {
        super(collection, nameFilter, elementFilter, instantiator, namer);
        this.parentMutationGuard = parentMutationGuard;
    }

    @Override
    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(CollectionFilter<S> elementFilter) {
        return Cast.uncheckedNonnullCast(getInstantiator().newInstance(DefaultNamedDomainObjectSet.class, this, elementFilter, getInstantiator(), getNamer()));
    }

    @Override
    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(Spec<String> nameFilter, CollectionFilter<S> elementFilter) {
        return Cast.uncheckedNonnullCast(getInstantiator().newInstance(DefaultNamedDomainObjectSet.class, this, nameFilter, elementFilter, getInstantiator(), getNamer()));
    }

    @Override
    public String getDisplayName() {
        return getTypeDisplayName() + " set";
    }

    @Override
    public <S extends T> NamedDomainObjectSet<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public NamedDomainObjectSet<T> named(Spec<String> nameFilter) {
        Spec<T> spec = convertNameToElementFilter(nameFilter);
        return filtered(nameFilter, createFilter(spec));
    }

    @Override
    public NamedDomainObjectSet<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public NamedDomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public Set<T> findAll(Closure cl) {
        return findAll(cl, new LinkedHashSet<T>());
    }

    @Override
    protected <I extends T> Action<? super I> wrapLazyAction(Action<? super I> action) {
        return parentMutationGuard.wrapLazyAction(super.wrapLazyAction(action));
    }
}
