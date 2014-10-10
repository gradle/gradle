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

package org.gradle.api.internal.plugins;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.plugins.AppliedPlugins;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.ExecutionException;

public class DefaultAppliedPlugins implements AppliedPlugins {

    private final AppliedPluginContainer container;

    private final LoadingCache<PluginIdLookupCacheKey, Boolean> idLookupCache;

    public DefaultAppliedPlugins(AppliedPluginContainer container, PluginRegistry pluginRegistry) {
        this.container = container;
        idLookupCache = CacheBuilder.newBuilder().build(new PluginIdLookupCacheLoader(pluginRegistry));
    }

    private DomainObjectCollection<Class<?>> matchingForId(final String id) {
        return container.matching(new Spec<Class<?>>() {
            public boolean isSatisfiedBy(Class<?> pluginClass) {
                try {
                    return idLookupCache.get(new PluginIdLookupCacheKey(pluginClass, id));
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    public Class<?> findPlugin(String id) {
        return Iterables.find(matchingForId(id), new Predicate<Class<?>>() {
            public boolean apply(Class<?> input) {
                return true;
            }
        }, null);
    }

    public boolean contains(String id) {
        return !matchingForId(id).isEmpty();
    }

    public void withPlugin(String id, Action<? super Class<?>> action) {
        matchingForId(id).all(action);
    }
}
