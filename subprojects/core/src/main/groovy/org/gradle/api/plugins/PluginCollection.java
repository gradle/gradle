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
package org.gradle.api.plugins;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.specs.Spec;

/**
 * <p>A {@code PluginCollection} represents a collection of {@link org.gradle.api.Plugin} instances.</p>
 * 
 * @param <T> The type of plugins which this collection contains.
 */
public interface PluginCollection<T extends Plugin> extends DomainObjectSet<T> {
    /**
     * {@inheritDoc}
     */
    PluginCollection<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    PluginCollection<T> matching(Closure closure);

    /**
     * {@inheritDoc}
     */
    <S extends T> PluginCollection<S> withType(Class<S> type);

    /**
     * Adds an {@code Action} to be executed when a plugin is added to this collection.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    @SuppressWarnings("UnusedDeclaration")
    Action<? super T> whenPluginAdded(Action<? super T> action);

    /**
     * Adds a closure to be called when a plugin is added to this collection. The plugin is passed to the closure as the
     * parameter.
     *
     * @param closure The closure to be called
     */
    @SuppressWarnings("UnusedDeclaration")
    void whenPluginAdded(Closure closure);

}
