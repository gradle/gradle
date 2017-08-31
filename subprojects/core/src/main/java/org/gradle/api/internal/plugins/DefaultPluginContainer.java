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
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.plugin.use.internal.DefaultPluginId;

public class DefaultPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {

    private final PluginRegistry pluginRegistry;
    private final PluginManagerInternal pluginManager;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, final PluginManagerInternal pluginManager) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.pluginManager = pluginManager;

        // Need this to make withId() work when someone does project.plugins.add(new SomePlugin());
        whenObjectAdded(new Action<Plugin>() {
            public void execute(Plugin plugin) {
                pluginManager.addImperativePlugin(plugin.getClass());
            }
        });
    }

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

    public <P extends Plugin> P apply(Class<P> type) {
        return pluginManager.addImperativePlugin(type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    private Plugin doFindPlugin(String id) {
        for (final PluginManagerInternal.PluginWithId pluginWithId : pluginManager.pluginsForId(id)) {
            Plugin plugin = Iterables.tryFind(DefaultPluginContainer.this, new Predicate<Plugin>() {
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

    public Plugin findPlugin(String id) {
        return doFindPlugin(id);
    }

    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
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
        P plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    public void withId(final String pluginId, final Action<? super Plugin> action) {
        Action<DefaultPluginManager.PluginWithId> wrappedAction = new Action<DefaultPluginManager.PluginWithId>() {
            public void execute(final DefaultPluginManager.PluginWithId pluginWithId) {
                matching(new Spec<Plugin>() {
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
