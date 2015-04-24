/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Set;

public class CloseableCollectionBuilder<E> implements CollectionBuilder<E>, Closeable {

    private final CollectionBuilder<E> delegate;
    private final ModelType<?> type;
    private final ModelRuleDescriptor ruleDescriptor;

    private boolean closed;

    public CloseableCollectionBuilder(CollectionBuilder<E> delegate, ModelType<?> type, ModelRuleDescriptor ruleDescriptor) {
        this.delegate = delegate;
        this.type = type;
        this.ruleDescriptor = ruleDescriptor;
    }

    @Override
    public <S> CollectionBuilder<S> withType(Class<S> type) {
        return delegate.withType(type);
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
    public E get(Object name) {
        return delegate.get(name);
    }

    @Override
    @Nullable
    public E get(String name) {
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
        assertNotClosed();
        delegate.create(name);
    }

    @Override
    public void create(String name, Action<? super E> configAction) {
        assertNotClosed();
        delegate.create(name, configAction);
    }

    @Override
    public <S extends E> void create(String name, Class<S> type) {
        assertNotClosed();
        delegate.create(name, type);
    }

    @Override
    public <S extends E> void create(String name, Class<S> type, Action<? super S> configAction) {
        assertNotClosed();
        delegate.create(name, type, configAction);
    }

    @Override
    public void named(String name, Action<? super E> configAction) {
        assertNotClosed();
        delegate.named(name, configAction);
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        assertNotClosed();
        delegate.named(name, ruleSource);
    }

    @Override
    public void beforeEach(Action<? super E> configAction) {
        assertNotClosed();
        delegate.beforeEach(configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        assertNotClosed();
        delegate.beforeEach(type, configAction);
    }

    @Override
    public void all(Action<? super E> configAction) {
        assertNotClosed();
        delegate.all(configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        assertNotClosed();
        delegate.withType(type, configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        assertNotClosed();
        delegate.withType(type, rules);
    }

    @Override
    public void afterEach(Action<? super E> configAction) {
        assertNotClosed();
        delegate.afterEach(configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        assertNotClosed();
        delegate.afterEach(type, configAction);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public void close() {
        closed = true;
    }

    private void assertNotClosed() {
        if (closed) {
            throw new ModelViewClosedException(type, ruleDescriptor);
        }
    }

}
