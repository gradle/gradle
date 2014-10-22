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

import com.google.common.collect.Sets;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;

import java.util.Set;

@NotThreadSafe
public class PluginManager {

    private final PluginApplicator applicator;
    private final PluginRegistry pluginRegistry;
    private final DefaultPluginContainer pluginContainer;
    private final Set<Class<?>> allPlugins = Sets.newHashSet();
    private final Set<String> unclaimedIds = Sets.newHashSet();
    private final Set<Class<?>> noIdPlugins = Sets.newHashSet();
    private final DomainObjectSet<PluginWithId> pluginsWithIds = new DefaultDomainObjectSet<PluginWithId>(PluginWithId.class, Sets.<PluginWithId>newHashSet());

    public PluginManager(PluginRegistry pluginRegistry, Instantiator instantiator, final PluginApplicator applicator) {
        this.applicator = applicator;
        this.pluginRegistry = pluginRegistry;
        this.pluginContainer = new DefaultPluginContainer(pluginRegistry, instantiator, new PluginApplicator() {
            public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
                applicator.applyImperative(pluginId, plugin);
                addPlugin(pluginId, plugin.getClass());
            }

            public void applyRules(@Nullable String pluginId, Class<?> clazz) {
                // plugin container should never try to apply such plugins
                throw new UnsupportedOperationException();
            }

            public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin) {
                addPlugin(pluginId, plugin.getClass());
                applicator.applyImperativeRulesHybrid(pluginId, plugin);
            }
        });
    }

    private void addPlugin(String pluginId, Class<?> pluginClass) {
        allPlugins.add(pluginClass);
        if (pluginId == null) {
            for (String unclaimedId : unclaimedIds) {
                if (pluginRegistry.hasId(pluginClass, unclaimedId)) {
                    addPlugin(unclaimedId, pluginClass);
                    return;
                }
            }

            for (PluginWithId pluginWithId : pluginsWithIds) {
                if (pluginRegistry.hasId(pluginClass, pluginWithId.id)) {
                    addPlugin(pluginWithId.id, pluginClass); // will fail
                    return;
                }
            }

            noIdPlugins.add(pluginClass);
        } else {
            for (PluginWithId plugin : pluginsWithIds) {
                if (plugin.id.equals(pluginId)) {
                    throw new InvalidPluginException("Cannot apply plugin '" + pluginId + "' of type " + pluginClass.getName() + " as a plugin of type " + plugin.clazz.getName() + " has already been applied with this id");

                }
            }
            pluginsWithIds.add(new PluginWithId(pluginId, pluginClass));
        }
    }

    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }

    public void apply(String pluginId) {
        PotentialPlugin potentialPlugin = pluginRegistry.lookup(pluginId);
        doApply(pluginId, potentialPlugin);
    }

    public void apply(Class<?> type) {
        PotentialPlugin potentialPlugin = pluginRegistry.inspect(type);
        doApply(null, potentialPlugin);
    }

    private void doApply(@Nullable String pluginId, PotentialPlugin potentialPlugin) {
        Class<?> pluginClass = potentialPlugin.asClass();
        if (potentialPlugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
            throw new IllegalArgumentException("'" + pluginClass.getName() + "' is neither a plugin or a rule source and cannot be applied.");
        } else if (!isApplied(pluginClass)) {
            Class<? extends Plugin<?>> asImperativeClass = potentialPlugin.asImperativeClass();
            if (asImperativeClass == null) {
                applicator.applyRules(pluginId, pluginClass);
                addPlugin(pluginId, pluginClass);
            } else {
                if (pluginId == null) {
                    pluginContainer.doApply(asImperativeClass);
                } else {
                    pluginContainer.doApply(pluginId);
                }
            }
        }
    }

    private boolean isApplied(final Class<?> pluginClass) {
        return allPlugins.contains(pluginClass);
    }

    private class PluginWithId {
        final String id;
        final Class<?> clazz;

        private PluginWithId(String id, Class<?> clazz) {
            this.id = id;
            this.clazz = clazz;
        }

        AppliedPlugin asAppliedPlugin() {
            return new DefaultAppliedPlugin(PluginId.unvalidated(id));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginWithId that = (PluginWithId) o;

            return clazz.equals(that.clazz) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + clazz.hashCode();
            return result;
        }
    }

    public AppliedPlugin findPlugin(final String id) {
        for (PluginWithId plugin : pluginsWithIds) {
            if (plugin.id.equals(id)) {
                return plugin.asAppliedPlugin();
            }
        }

        for (Class<?> noIdPlugin : noIdPlugins) {
            if (pluginRegistry.hasId(noIdPlugin, id)) {
                PluginWithId pluginWithId = new PluginWithId(id, noIdPlugin);
                pluginsWithIds.add(pluginWithId);
                noIdPlugins.remove(noIdPlugin);
                return pluginWithId.asAppliedPlugin();
            }
        }

        unclaimedIds.add(id);
        return null;
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public void withPlugin(final String id, final Action<? super AppliedPlugin> action) {
        findPlugin(id);

        pluginsWithIds.matching(new Spec<PluginWithId>() {
            public boolean isSatisfiedBy(PluginWithId element) {
                return element.id.equals(id);
            }
        }).all(new Action<PluginWithId>() {
            public void execute(PluginWithId pluginWithId) {
                action.execute(pluginWithId.asAppliedPlugin());
            }
        });
    }
}

