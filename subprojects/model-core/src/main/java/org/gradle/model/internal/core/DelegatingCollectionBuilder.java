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

package org.gradle.model.internal.core;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.BiAction;
import org.gradle.internal.Cast;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.type.ModelType;

import java.util.Set;

public class DelegatingCollectionBuilder<T> implements CollectionBuilder<T> {

    private final CollectionBuilder<T> delegate;
    private final ModelType<T> baseType;
    private final BiAction<? super String, ? super ModelType<? extends T>> onCreate;

    public DelegatingCollectionBuilder(CollectionBuilder<T> delegate, ModelType<T> baseType, BiAction<? super String, ? super ModelType<? extends T>> onCreate) {
        this.delegate = delegate;
        this.baseType = baseType;
        this.onCreate = onCreate;
    }

    @Override
    public <S> CollectionBuilder<S> withType(Class<S> type) {
        // This cast is safe, because we know that .create() will fail on the real collection if S doesn't extend T
        BiAction<? super String, ? super ModelType<? extends S>> castOnCreate = Cast.uncheckedCast(onCreate);
        return new DelegatingCollectionBuilder<S>(delegate.withType(type), ModelType.of(type), castOnCreate);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    @Nullable
    public T get(Object name) {
        return delegate.get(name);
    }

    @Override
    @Nullable
    public T get(String name) {
        return delegate.get(name);
    }

    @Override
    public boolean containsKey(Object name) {
        return delegate.containsKey(name);
    }

    @Override
    public boolean containsValue(Object item) {
        return delegate.containsValue(item);
    }

    @Override
    public Set<String> keySet() {
        return delegate.keySet();
    }

    @Override
    public void create(String name) {
        delegate.create(name);
        onCreate(name, baseType);
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        delegate.create(name, configAction);
        onCreate(name, baseType);
    }

    @Override
    public <S extends T> void create(String name, Class<S> type) {
        delegate.create(name, type);
        onCreate(name, ModelType.of(type));
    }

    @Override
    public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
        onCreate(name, ModelType.of(type));
        delegate.create(name, type, configAction);
    }

    @Override
    public void named(String name, Action<? super T> configAction) {
        delegate.named(name, configAction);
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        delegate.named(name, ruleSource);
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        delegate.beforeEach(configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        delegate.beforeEach(type, configAction);
    }

    @Override
    public void all(Action<? super T> configAction) {
        delegate.all(configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        delegate.withType(type, configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        delegate.withType(type, rules);
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        delegate.afterEach(configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        delegate.afterEach(type, configAction);
    }

    private <S extends T> void onCreate(String name, ModelType<S> type) {
        onCreate.execute(name, type);
    }
}
