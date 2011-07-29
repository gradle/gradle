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
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.*;

import org.gradle.api.internal.collections.FilteredSet;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.CollectionEventRegister;

public class DefaultNamedDomainObjectSet<T> extends DefaultNamedDomainObjectCollection<T> implements NamedDomainObjectSet<T> {

    public DefaultNamedDomainObjectSet(Class<T> type, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, new TreeSet(new Namer.Comparator(namer)), classGenerator, namer);
    }

    public DefaultNamedDomainObjectSet(Class<T> type, ClassGenerator classGenerator) {
        this(type, classGenerator, Named.Namer.forType(type));
    }

    /**
     * Subclasses using this constructor must ensure that the {@code store} uses a name based equality strategy as per the contract on NamedDomainObjectContainer.
     */
    protected DefaultNamedDomainObjectSet(Class<T> type, Set<T> store, CollectionEventRegister<T> eventRegister, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, store, eventRegister, classGenerator, namer);
    }

    // should be protected, but use of the class generator forces it to be public
    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> collection, CollectionFilter<T> filter, ClassGenerator classGenerator, Namer<? super T> namer) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter), classGenerator, namer);
    }

    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(CollectionFilter<S> filter) {
        return getClassGenerator().newInstance(DefaultNamedDomainObjectSet.class, this, filter, getClassGenerator(), getNamer());
    }

    protected <S extends T> Set<S> filteredStore(CollectionFilter<S> filter) {
        return new FilteredSet<T, S>(this, filter);
    }

    public String getDisplayName() {
        return String.format("%s set", getTypeDisplayName());
    }

    public <S extends T> NamedDomainObjectSet<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    public NamedDomainObjectSet<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public NamedDomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    // Overridden to allow the backing store to enforce uniqueness.
    public boolean add(T o) {
        return super.add(o);
    }

}
