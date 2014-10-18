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
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;

import java.util.concurrent.ExecutionException;

public class DefaultPluginRegistry implements PluginRegistry {

    private final LoadingCache<Class<?>, PotentialPlugin> classMappings;
    private final LoadingCache<PluginIdLookupCacheKey, Boolean> pluginClassIdCache;
    private final LoadingCache<String, Optional<PotentialPlugin>> idMappings;

    private final DefaultPluginRegistry parent;
    private final PluginInspector pluginInspector;

    public DefaultPluginRegistry(PluginInspector pluginInspector, ClassLoader classLoader) {
        this(
                null,
                pluginInspector,
                CacheBuilder.newBuilder().build(new PotentialPluginCacheLoader(pluginInspector)),
                CacheBuilder.newBuilder().build(new PluginIdCacheLoader()),
                Factories.constant(classLoader)
        );
    }

    private DefaultPluginRegistry(DefaultPluginRegistry parent, PluginInspector pluginInspector, LoadingCache<Class<?>, PotentialPlugin> classMappings, LoadingCache<PluginIdLookupCacheKey, Boolean> pluginClassIdCache, final Factory<? extends ClassLoader> classLoaderFactory) {
        this.parent = parent;
        this.pluginInspector = pluginInspector;
        this.classMappings = classMappings;
        this.pluginClassIdCache = pluginClassIdCache;

        this.idMappings = CacheBuilder.newBuilder().build(new CacheLoader<String, Optional<PotentialPlugin>>() {
            @Override
            public Optional<PotentialPlugin> load(@SuppressWarnings("NullableProblems") String pluginId) throws Exception {
                ClassLoader classLoader = classLoaderFactory.create();
                PluginDescriptor pluginDescriptor = findPluginDescriptor(pluginId, classLoader);
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

                PotentialPlugin potentialPlugin = inspect(implClass);
                if (potentialPlugin == null) {
                    throw new InvalidPluginException("Implementation class " + implClassName + " for plugin with id '" + pluginId + "' is not a valid plugin implementation.");
                } else {
                    return Optional.of(potentialPlugin);
                }
            }
        });
    }

    public PluginRegistry createChild(final ClassLoaderScope lookupScope) {
        return new DefaultPluginRegistry(this, pluginInspector, classMappings, pluginClassIdCache, new Factory<ClassLoader>() {
            public ClassLoader create() {
                return lookupScope.getLocalClassLoader();
            }
        });
    }

    private Boolean internalHasId(Class<?> pluginClass, String id) {
        if (parent != null) {
            Boolean parentHas = parent.internalHasId(pluginClass, id);
            if (parentHas != null) {
                return parentHas;
            }
        }

        Optional<PotentialPlugin> potentialPlugin = find(id);
        if (potentialPlugin.isPresent() && potentialPlugin.get().asClass().equals(pluginClass)) {
            return true;
        } else {
            return uncheckedGet(pluginClassIdCache, new PluginIdLookupCacheKey(pluginClass, id));
        }
    }

    public boolean hasId(Class<?> pluginClass, String id) {
        return internalHasId(pluginClass, id);
    }

    public PotentialPlugin inspect(Class<?> clazz) {
        return uncheckedGet(classMappings, clazz);
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

    public PotentialPlugin lookup(String pluginId) {
        Optional<PotentialPlugin> potentialPlugin = find(pluginId);
        if (potentialPlugin.isPresent()) {
            return potentialPlugin.get();
        } else {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }
    }

    private Optional<PotentialPlugin> find(String pluginId) {
        if (parent != null) {
            Optional<PotentialPlugin> fromParent = parent.find(pluginId);
            if (fromParent.isPresent()) {
                return fromParent;
            }
        }

        return uncheckedGet(idMappings, pluginId);
    }

    protected PluginDescriptor findPluginDescriptor(String pluginId, ClassLoader classLoader) {
        PluginDescriptorLocator pluginDescriptorLocator = new ClassloaderBackedPluginDescriptorLocator(classLoader);
        return pluginDescriptorLocator.findPluginDescriptor(pluginId);
    }

    static class PluginIdLookupCacheKey {

        private final Class<?> pluginClass;
        private final String id;

        PluginIdLookupCacheKey(Class<?> pluginClass, String id) {
            this.pluginClass = pluginClass;
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Class<?> getPluginClass() {
            return pluginClass;
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

            return id.equals(that.id) && pluginClass.equals(that.pluginClass);
        }

        @Override
        public int hashCode() {
            int result = pluginClass.hashCode();
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

    private static class PluginIdCacheLoader extends CacheLoader<PluginIdLookupCacheKey, Boolean> {
        @Override
        public Boolean load(@SuppressWarnings("NullableProblems") PluginIdLookupCacheKey key) throws Exception {
            Class<?> pluginClass = key.getPluginClass();
            PluginDescriptorLocator locator = new ClassloaderBackedPluginDescriptorLocator(pluginClass.getClassLoader());
            PluginDescriptor pluginDescriptor = locator.findPluginDescriptor(key.getId());
            return pluginDescriptor != null && pluginDescriptor.getImplementationClassName().equals(pluginClass.getName());
        }
    }
}