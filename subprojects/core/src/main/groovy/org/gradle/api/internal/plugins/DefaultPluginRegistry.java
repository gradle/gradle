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

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.internal.Cast;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.plugin.internal.PluginId;
import org.gradle.util.GUtil;

import java.util.concurrent.ExecutionException;

public class DefaultPluginRegistry implements PluginRegistry {

    private final PluginRegistry parent;
    private final PluginInspector pluginInspector;
    private final Factory<? extends ClassLoader> classLoaderFactory;

    private final LoadingCache<Class<?>, PotentialPlugin> classMappings;
    private final LoadingCache<PluginIdLookupCacheKey, Optional<PotentialPluginWithId<?>>> idMappings;

    public DefaultPluginRegistry(PluginInspector pluginInspector, ClassLoader classLoader) {
        this(null, pluginInspector, Factories.constant(classLoader));
    }

    private DefaultPluginRegistry(PluginRegistry parent, PluginInspector pluginInspector, final Factory<? extends ClassLoader> classLoaderFactory) {
        this.parent = parent;
        this.pluginInspector = pluginInspector;
        this.classLoaderFactory = classLoaderFactory;
        this.classMappings = CacheBuilder.newBuilder().build(new PotentialPluginCacheLoader(pluginInspector));
        this.idMappings = CacheBuilder.newBuilder().build(new CacheLoader<PluginIdLookupCacheKey, Optional<PotentialPluginWithId<?>>>() {
            @Override
            public Optional<PotentialPluginWithId<?>> load(@SuppressWarnings("NullableProblems") PluginIdLookupCacheKey key) throws Exception {
                String pluginId = key.getId();
                ClassLoader classLoader = key.getClassLoader();

                PluginDescriptorLocator locator = new ClassloaderBackedPluginDescriptorLocator(classLoader);

                PluginDescriptor pluginDescriptor = locator.findPluginDescriptor(pluginId);
                if (pluginDescriptor == null) {
                    return Optional.absent();
                }

                String implClassName = pluginDescriptor.getImplementationClassName();
                if (!GUtil.isTrue(implClassName)) {
                    throw new PluginInstantiationException(String.format("No implementation class specified for plugin '%s' in %s.", pluginId, pluginDescriptor));
                }

                Class<?> implClass;
                try {
                    implClass = classLoader.loadClass(implClassName);
                } catch (ClassNotFoundException e) {
                    throw new InvalidPluginException(String.format(
                            "Could not find implementation class '%s' for plugin '%s' specified in %s.", implClassName, pluginId,
                            pluginDescriptor), e);
                }


                PotentialPlugin<?> potentialPlugin = inspect(implClass);
                PotentialPluginWithId<?> withId = PotentialPluginWithId.of(PluginId.unvalidated(pluginId), potentialPlugin);
                return Cast.uncheckedCast(Optional.of(withId));
            }

        });
    }

    public PluginRegistry createChild(final ClassLoaderScope lookupScope) {
        return new DefaultPluginRegistry(this, pluginInspector, new Factory<ClassLoader>() {
            public ClassLoader create() {
                return lookupScope.getLocalClassLoader();
            }
        });
    }

    public <T> PotentialPlugin<T> inspect(Class<T> clazz) {
        // Don't go up the parent chain.
        // Don't want to risk classes crossing “scope” boundaries and being non collectible.
        return Cast.uncheckedCast(uncheckedGet(classMappings, clazz));
    }

    public PotentialPluginWithId lookup(String idOrName) {
        PotentialPluginWithId lookup;
        if (parent != null) {
            lookup = parent.lookup(idOrName);
            if (lookup == null) {
                String qualified = DefaultPluginManager.maybeQualify(idOrName);
                if (qualified != null) {
                    lookup = lookup(qualified);
                }
            }

            if (lookup != null) {
                return lookup;
            }
        }

        return lookup(idOrName, classLoaderFactory.create());
    }

    public PotentialPluginWithId lookup(String idOrName, ClassLoader classLoader) {
        // Don't go up the parent chain.
        // Don't want to risk classes crossing “scope” boundaries and being non collectible.
        PotentialPluginWithId lookup = uncheckedGet(idMappings, new PluginIdLookupCacheKey(idOrName, classLoader)).orNull();
        if (lookup == null) {
            String qualified = DefaultPluginManager.maybeQualify(idOrName);
            if (qualified != null) {
                lookup = uncheckedGet(idMappings, new PluginIdLookupCacheKey(qualified, classLoader)).orNull();
            }
        }

        return lookup;
    }

    private static <K, V> V uncheckedGet(LoadingCache<K, V> cache, K key) {
        try {
            return cache.get(key);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    static class PluginIdLookupCacheKey {

        private final ClassLoader classLoader;
        private final String id;

        PluginIdLookupCacheKey(String id, ClassLoader classLoader) {
            this.classLoader = classLoader;
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginIdLookupCacheKey that = (PluginIdLookupCacheKey) o;

            return classLoader.equals(that.classLoader) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = classLoader.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    private static class PotentialPluginCacheLoader extends CacheLoader<Class<?>, PotentialPlugin> {
        private final PluginInspector pluginInspector;

        public PotentialPluginCacheLoader(PluginInspector pluginInspector) {
            this.pluginInspector = pluginInspector;
        }

        @Override
        public PotentialPlugin load(@SuppressWarnings("NullableProblems") Class<?> key) throws Exception {
            return pluginInspector.inspect(key);
        }
    }

}