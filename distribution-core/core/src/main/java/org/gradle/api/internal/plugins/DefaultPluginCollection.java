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
package org.gradle.api.internal.plugins;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.Collection;

@SuppressWarnings("deprecation") // Something is weird with the hierarchy of 'add(T)', the implementation inherited from DefaultDomainObjectSet is used internally in DefaultPluginContainer.pluginAdded() but at the same time PluginCollection.add(T) is deprecated
class DefaultPluginCollection<T extends Plugin> extends DefaultDomainObjectSet<T> implements PluginCollection<T> {
    DefaultPluginCollection(Class<T> type, CollectionCallbackActionDecorator decorator) {
        super(type, decorator);
    }

    private DefaultPluginCollection(DefaultPluginCollection<? super T> collection, CollectionFilter<T> filter) {
        super(collection, filter);
    }

    @Override
    protected <S extends T> DefaultPluginCollection<S> filtered(CollectionFilter<S> filter) {
        return new DefaultPluginCollection<S>(this, filter);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S extends T> PluginCollection<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public PluginCollection<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public PluginCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public Action<? super T> whenPluginAdded(Action<? super T> action) {
        return whenObjectAdded(action);
    }

    @Override
    public void whenPluginAdded(Closure closure) {
        whenObjectAdded(closure);
    }

}
