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
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.IterationOrderRetainingSetElementSource;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.ImmutableActionSet;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDomainObjectSet<T> extends DefaultDomainObjectCollection<T> implements DomainObjectSet<T> {
    // TODO: Combine these with MutationGuard
    private ImmutableActionSet<Void> beforeContainerChange = ImmutableActionSet.empty();

    public DefaultDomainObjectSet(Class<? extends T> type, CollectionCallbackActionDecorator decorator) {
        super(type, new IterationOrderRetainingSetElementSource<T>(), decorator);
    }

    /**
     * Adds an action which is executed before this collection is mutated with the addition or removal of elements.
     * Any exception thrown by the action will veto the mutation.
     *
     * TODO: Combine this with the MutationGuard or rework CompositeDomainObject to behave with MutationGuard/MutationValidator.
     * The mutation validators used in DefaultConfiguration only expect to be used with add/remove methods and fail when we
     * correctly try to also prevent all/withType/etc mutation methods.
     *
     * assertMutableCollectionContents is only used by add/remove methods, but we should remove this special handling and fix
     * DefaultConfiguration and CompositeDomainObjects.
     */
    public void beforeCollectionChanges(Action<Void> action) {
        beforeContainerChange = beforeContainerChange.add(action);
    }

    @Override
    protected void assertMutableCollectionContents() {
        beforeContainerChange.execute(null);
    }

    public DefaultDomainObjectSet(Class<? extends T> type, ElementSource<T> store, CollectionCallbackActionDecorator decorator) {
        super(type, store, decorator);
    }

    protected DefaultDomainObjectSet(DefaultDomainObjectSet<? super T> store, CollectionFilter<T> filter) {
        this(filter.getType(), store.filteredStore(filter), store.filteredEvents(filter));
    }

    protected DefaultDomainObjectSet(Class<? extends T> type, ElementSource<T> store, CollectionEventRegister<T> eventRegister) {
        super(type, store, eventRegister);
    }

    @Override
    protected <S extends T> DefaultDomainObjectSet<S> filtered(CollectionFilter<S> filter) {
        return new DefaultDomainObjectSet<S>(this, filter);
    }

    @Override
    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public DomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public Set<T> findAll(Closure cl) {
        return findAll(cl, new LinkedHashSet<T>());
    }
}
