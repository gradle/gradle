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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.Collection;

/**
 * This plugin collection is optimized based on the knowledge we have about how plugins
 * are applied. The plugin manager already keeps track of all plugins and ensures they
 * are only applied once. As a result, we don't need to keep another data structure here,
 * but can just share the one kept by the manager. This class forbids all mutations, as
 * manually adding/removing plugin instances does not make sense.
 */
public class DefaultPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {

    private final PluginRegistry pluginRegistry;
    private final PluginManagerInternal pluginManager;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, final PluginManagerInternal pluginManager, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(Plugin.class, callbackActionDecorator);
        this.pluginRegistry = pluginRegistry;
        this.pluginManager = pluginManager;
    }

    void pluginAdded(Plugin plugin) {
        super.add(plugin);
    }

    @Override
    public boolean add(Plugin toAdd) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends Plugin> c) {
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
    public Plugin apply(String id) {
        PluginImplementation plugin = pluginRegistry.lookup(DefaultPluginId.unvalidated(id));
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id '" + id + "' not found.");
        }

        if (!Plugin.class.isAssignableFrom(plugin.asClass())) {
            throw new IllegalArgumentException("Plugin implementation '" + plugin.asClass().getName() + "' does not implement the Plugin interface. This plugin cannot be applied directly via the PluginContainer.");
        } else {
            return pluginManager.addImperativePlugin(plugin);
        }
    }

    @Override
    public <P extends Plugin> P apply(Class<P> type) {
        return pluginManager.addImperativePlugin(type);
    }

    @Override
    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    @Override
    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    private Plugin doFindPlugin(String id) {
        for (final PluginManagerInternal.PluginWithId pluginWithId : pluginManager.pluginsForId(id)) {
            Plugin plugin = Iterables.tryFind(DefaultPluginContainer.this, new Predicate<Plugin>() {
                @Override
                public boolean apply(Plugin plugin) {
                    return pluginWithId.clazz.equals(plugin.getClass());
                }
            }).orNull();

            if (plugin != null) {
                return plugin;
            }
        }

        return null;
    }

    @Override
    public Plugin findPlugin(String id) {
        return doFindPlugin(id);
    }

    @Override
    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    @Override
    public Plugin getPlugin(String id) {
        Plugin plugin = findPlugin(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    @Override
    public Plugin getAt(String id) throws UnknownPluginException {
        return getPlugin(id);
    }

    @Override
    public <P extends Plugin> P getAt(Class<P> type) throws UnknownPluginException {
        return getPlugin(type);
    }

    @Override
    public <P extends Plugin> P getPlugin(Class<P> type) throws UnknownPluginException {
        P plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    @Override
    public void withId(final String pluginId, final Action<? super Plugin> action) {
        Action<DefaultPluginManager.PluginWithId> wrappedAction = new Action<DefaultPluginManager.PluginWithId>() {
            @Override
            public void execute(final DefaultPluginManager.PluginWithId pluginWithId) {
                matching(new Spec<Plugin>() {
                    @Override
                    public boolean isSatisfiedBy(Plugin element) {
                        return pluginWithId.clazz.equals(element.getClass());
                    }
                }).all(action);
            }
        };

        pluginManager.pluginsForId(pluginId).all(wrappedAction);
    }

    @Override
    public <S extends Plugin> PluginCollection<S> withType(Class<S> type) {
        // runtime check because method is used from Groovy where type bounds are not respected
        if (!Plugin.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(String.format("'%s' does not implement the Plugin interface.", type.getName()));
        }

        return super.withType(type);
    }
}
