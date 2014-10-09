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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DefaultPluginContainer<T extends PluginAwareInternal> extends DefaultPluginCollection<Plugin> implements PluginContainer {
    private PluginRegistry pluginRegistry;

    private static class IdLookupCacheKey {
        private final Class<?> pluginClass;
        private final String id;

        private IdLookupCacheKey(Class<?> pluginClass, String id) {
            this.pluginClass = pluginClass;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IdLookupCacheKey that = (IdLookupCacheKey) o;

            return id.equals(that.id) && pluginClass.equals(that.pluginClass);
        }

        @Override
        public int hashCode() {
            int result = pluginClass.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    private final LoadingCache<IdLookupCacheKey, Boolean> idLookupCache = CacheBuilder.newBuilder().build(new CacheLoader<IdLookupCacheKey, Boolean>() {
        @Override
        public Boolean load(@SuppressWarnings("NullableProblems") IdLookupCacheKey key) throws Exception {
            Class<?> pluginClass = key.pluginClass;

            // Plugin registry will have the mapping cached in memory for most plugins, try first
            try {
                Class<? extends Plugin<?>> typeForId = pluginRegistry.getPluginTypeForId(key.id);
                if (typeForId.equals(pluginClass)) {
                    return true;
                }
            } catch (UnknownPluginException ignore) {
                // ignore
            }

            PluginDescriptorLocator locator = new ClassloaderBackedPluginDescriptorLocator(pluginClass.getClassLoader());
            PluginDescriptor pluginDescriptor = locator.findPluginDescriptor(key.id);
            return pluginDescriptor != null && pluginDescriptor.getImplementationClassName().equals(pluginClass.getName());
        }
    });

    private final T pluginAware;
    private final List<PluginApplicationAction> pluginApplicationActions;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, T pluginAware) {
        this(pluginRegistry, pluginAware, Collections.<PluginApplicationAction>emptyList());
    }

    public DefaultPluginContainer(PluginRegistry pluginRegistry, T pluginAware, List<PluginApplicationAction> pluginApplicationActions) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.pluginAware = pluginAware;
        this.pluginApplicationActions = pluginApplicationActions;
    }

    public Plugin apply(String id) {
        return addPluginInternal(getPluginTypeForId(id));
    }

    public <P extends Plugin> P apply(Class<P> type) {
        return addPluginInternal(type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    public Plugin findPlugin(String id) {
        try {
            return findPlugin(getPluginTypeForId(id));
        } catch (UnknownPluginException e) {
            return null;
        }
    }

    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    private <P extends Plugin<?>> P addPluginInternal(Class<P> type) {
        if (findPlugin(type) == null) {
            Plugin plugin = providePlugin(type);
            for (PluginApplicationAction onApplyAction : pluginApplicationActions) {
                onApplyAction.execute(new PluginApplication(plugin, pluginAware));
            }
            add(plugin);
        }
        return type.cast(findPlugin(type));
    }

    public Plugin getPlugin(String id) {
        Plugin plugin = findPlugin(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    public Plugin getAt(String id) throws UnknownPluginException {
        return getPlugin(id);
    }

    public <P extends Plugin> P getAt(Class<P> type) throws UnknownPluginException {
        return getPlugin(type);
    }

    public <P extends Plugin> P getPlugin(Class<P> type) throws UnknownPluginException {
        Plugin plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    public void withId(final String pluginId, Action<? super Plugin> action) {
        try {
            Class<?> typeForId = pluginRegistry.getTypeForId(pluginId);
            if (!Plugin.class.isAssignableFrom(typeForId) && !new ModelRuleSourceDetector().getDeclaredSources(typeForId).isEmpty()) {
                String message = String.format("The type for id '%s' (class: '%s') is a rule source and not a plugin. Use AppliedPlugins.withPlugin() to perform an action if a rule source is applied.", pluginId, typeForId.getName());
                throw new IllegalArgumentException(message);
            }
        } catch (UnknownPluginException e) {
            //just continue if it's impossible to see if the id points to a plain rule source
        }
        matching(new Spec<Plugin>() {
            public boolean isSatisfiedBy(Plugin element) {
                try {
                    return idLookupCache.get(new IdLookupCacheKey(element.getClass(), pluginId));
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }).all(action);
    }

    protected Class<? extends Plugin> getPluginTypeForId(String id) {
        return pluginRegistry.getPluginTypeForId(id);
    }

    private Plugin<T> providePlugin(Class<? extends Plugin<?>> type) {
        @SuppressWarnings("unchecked") Plugin<T> plugin = (Plugin<T>) pluginRegistry.loadPlugin(type);
        plugin.apply(pluginAware);
        return plugin;
    }
}
