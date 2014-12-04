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
import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;

public class DefaultPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {

    private final PluginRegistry pluginRegistry;
    private final Instantiator instantiator;
    private final PluginApplicator applicator;
    private final PluginManager pluginManager;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, final PluginManager pluginManager, Instantiator instantiator, PluginApplicator applicator) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.pluginManager = pluginManager;
        this.instantiator = instantiator;
        this.applicator = applicator;

        // Need this to make withId() work when someone does project.plugins.add(new SomePlugin());
        whenObjectAdded(new Action<Plugin>() {
            public void execute(Plugin plugin) {
                pluginManager.addPluginDirect(plugin.getClass());
            }
        });
    }

    public Plugin apply(String id) {
        PotentialPluginWithId potentialPlugin = pluginRegistry.lookup(id);
        if (potentialPlugin == null) {
            throw new UnknownPluginException("Plugin with id '" + id + "' not found.");
        }

        Class<? extends Plugin<?>> pluginClass = potentialPlugin.asImperativeClass();

        if (pluginClass == null) {
            throw new IllegalArgumentException("Plugin implementation '" + potentialPlugin.asClass().getName() + "' does not implement the Plugin interface. This plugin cannot be applied directly via the PluginContainer.");
        } else {
            return addPluginInternal(potentialPlugin.getPluginId().toString(), pluginClass);
        }
    }

    public <P extends Plugin> P apply(Class<P> type) {
        return addPluginInternal(null, type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    private Plugin doFindPlugin(String id) {
        for (final PluginManager.PluginWithId pluginWithId : pluginManager.pluginsForId(id)) {
            Plugin plugin = Iterables.find(DefaultPluginContainer.this, new Predicate<Plugin>() {
                public boolean apply(Plugin plugin) {
                    return pluginWithId.clazz.equals(plugin.getClass());
                }
            });

            if (plugin != null) {
                return plugin;
            }
        }

        return null;
    }

    public Plugin findPlugin(String id) {
        String qualified = PluginManager.maybeQualify(id);
        if (qualified != null) {
            Plugin plugin = doFindPlugin(qualified);
            if (plugin != null) {
                return plugin;
            }
        }

        Plugin plugin = doFindPlugin(id);
        if (plugin != null) {
            return plugin;
        }

        return null;
    }

    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    private <P extends Plugin<?>> P addPluginInternal(@Nullable String pluginId, Class<P> type) {
        try {
            P existing = findPlugin(type);
            if (existing == null) {
                P plugin = providePlugin(type);
                PotentialPlugin potentialPlugin = pluginRegistry.inspect(plugin.getClass());
                if (potentialPlugin.hasRules()) {
                    applicator.applyImperativeRulesHybrid(pluginId, plugin);
                } else {
                    applicator.applyImperative(pluginId, plugin);
                }

                doAdd(plugin);
                return plugin;
            } else {
                return existing;
            }
        } catch (PluginApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginApplicationException(pluginId == null ? "class '" + type.getName() + "'" : "id '" + pluginId + "'", e);
        }
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
        Action<PluginManager.PluginWithId> wrappedAction = new Action<PluginManager.PluginWithId>() {
            public void execute(final PluginManager.PluginWithId pluginWithId) {
                matching(new Spec<Plugin>() {
                    public boolean isSatisfiedBy(Plugin element) {
                        return pluginWithId.clazz.equals(element.getClass());
                    }
                }).all(action);
            }
        };

        String qualified = PluginManager.maybeQualify(pluginId);
        if (qualified != null) {
            pluginManager.pluginsForId(qualified).all(wrappedAction);
        }

        pluginManager.pluginsForId(pluginId).all(wrappedAction);
    }

    private <T extends Plugin<?>> T providePlugin(Class<T> type) {
        try {
            return instantiator.newInstance(type);
        } catch (ObjectInstantiationException e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.", type.getSimpleName()), e.getCause());
        }
    }

    @Override
    public <S extends Plugin> PluginCollection<S> withType(Class<S> type) {
        // runtime check because method is used from Groovy where type bounds are not respected
        if (!Plugin.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(String.format("'%s' does not implement the Plugin interface.", type.getName()));
        }

        return super.withType(type);
    }

    private boolean doAdd(Plugin toAdd) {
        return super.add(toAdd);
    }

}
