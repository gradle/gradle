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

package org.gradle.model;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.model.collection.CollectionBuilder;

import java.util.Collection;
import java.util.Set;

/**
 * Model backed map like structure allowing adding of items where instantiation is managed.
 * <p>
 * {@link org.gradle.model.Managed} types may declare model map properties.
 * Model maps can only contain managed types.
 *
 * @param <T> the contract type for all items
 */
@SuppressWarnings("deprecation")
@Incubating
public interface ModelMap<T> extends CollectionBuilder<T>, Iterable<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    <S> ModelMap<S> withType(Class<S> type);

    /**
     * {@inheritDoc}
     */
    @Override
    int size();

    /**
     * {@inheritDoc}
     */
    @Override
    boolean isEmpty();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    T get(Object name);

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    T get(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    boolean containsKey(Object name);

    /**
     * {@inheritDoc}
     */
    @Override
    boolean containsValue(Object item);

    /**
     * {@inheritDoc}
     */
    @Override
    Set<String> keySet();

    /**
     * {@inheritDoc}
     */
    @Override
    void create(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    void create(String name, Action<? super T> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    <S extends T> void create(String name, Class<S> type);

    /**
     * {@inheritDoc}
     */
    @Override
    <S extends T> void create(String name, Class<S> type, Action<? super S> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    void named(String name, Action<? super T> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    void named(String name, Class<? extends RuleSource> ruleSource);

    /**
     * {@inheritDoc}
     */
    @Override
    void beforeEach(Action<? super T> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    <S> void beforeEach(Class<S> type, Action<? super S> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    void all(Action<? super T> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    <S> void withType(Class<S> type, Action<? super S> configAction);

    @Override
    /**
     * {@inheritDoc}
     */
    <S> void withType(Class<S> type, Class<? extends RuleSource> rules);

    @Override
    /**
     * {@inheritDoc}
     */
    void afterEach(Action<? super T> configAction);

    @Override
    /**
     * {@inheritDoc}
     */
    <S> void afterEach(Class<S> type, Action<? super S> configAction);

    /**
     * {@inheritDoc}
     */
    @Override
    Collection<T> values();
}
