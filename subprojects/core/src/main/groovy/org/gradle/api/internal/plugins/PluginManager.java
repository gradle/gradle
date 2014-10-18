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
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.AppliedPlugins;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;

public class PluginManager {

    private final PluginApplicator applicator;
    private final PluginRegistry pluginRegistry;
    private final PluginContainer pluginContainer;

    private final DomainObjectSet<AppliedPluginImpl> plugins = new DefaultDomainObjectSet<AppliedPluginImpl>(AppliedPluginImpl.class, Sets.<AppliedPluginImpl>newHashSet());
    private final AppliedPluginsImpl appliedPlugins = new AppliedPluginsImpl();

    public PluginManager(PluginRegistry pluginRegistry, Instantiator instantiator, final PluginApplicator applicator) {
        this.applicator = applicator;
        this.pluginRegistry = pluginRegistry;
        this.pluginContainer = new DefaultPluginContainer(pluginRegistry, instantiator, new PluginApplicator() {
            public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
                applicator.applyImperative(pluginId, plugin);
                plugins.add(new AppliedPluginImpl(pluginId, plugin.getClass()));
            }

            public void applyRules(@Nullable String pluginId, Class<?> clazz) {
                // plugin container should never try to apply such plugins
                throw new UnsupportedOperationException();
            }

            public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin) {
                applyImperative(pluginId, plugin);
                applicator.applyRules(pluginId, plugin.getClass());
            }
        });
    }

    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }

    public AppliedPlugins getAppliedPlugins() {
        return appliedPlugins;
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
        if (potentialPlugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
            throw new IllegalArgumentException("'" + potentialPlugin.asClass().getName() + "' is neither a plugin or a rule source and cannot be applied.");
        } else if (!isApplied(potentialPlugin.asClass())) {
            Class<? extends Plugin<?>> asImperativeClass = potentialPlugin.asImperativeClass();
            if (asImperativeClass == null) {
                applicator.applyRules(pluginId, potentialPlugin.asClass());
                plugins.add(new AppliedPluginImpl(pluginId, potentialPlugin.asClass()));
            } else {
                if (pluginId == null) {
                    pluginContainer.apply(asImperativeClass);
                } else {
                    pluginContainer.apply(pluginId);
                }
            }
        }
    }

    private boolean isApplied(final Class<?> pluginClass) {
        return Iterables.any(plugins, new Predicate<AppliedPluginImpl>() {
            public boolean apply(AppliedPluginImpl input) {
                return input.getImplementationClass().equals(pluginClass);
            }
        });
    }

    private static class AppliedPluginImpl implements AppliedPlugin {
        private final PluginId pluginId;
        private final Class<?> implClass;


        public AppliedPluginImpl(@Nullable String pluginId, Class<?> implClass) {
            this.pluginId = pluginId == null ? null : PluginId.unvalidated(pluginId);
            this.implClass = implClass;
        }

        public String getId() {
            return pluginId == null ? null : pluginId.toString();
        }

        public String getNamespace() {
            return pluginId == null ? null : pluginId.getNamespace();
        }

        public String getName() {
            return pluginId == null ? null : pluginId.getName();
        }

        public Class<?> getImplementationClass() {
            return implClass;
        }
    }

    private class AppliedPluginsImpl implements AppliedPlugins {
        public AppliedPlugin findPlugin(final String id) {
            for (AppliedPluginImpl plugin : plugins) {
                if (plugin.getId() != null && plugin.getId().equals(id)) {
                    return plugin;
                }
            }

            return null;
        }

        public boolean contains(String id) {
            return findPlugin(id) != null;
        }

        public void withPlugin(final String id, Action<? super AppliedPlugin> action) {
            plugins.matching(new Spec<AppliedPluginImpl>() {
                public boolean isSatisfiedBy(AppliedPluginImpl element) {
                    return pluginRegistry.hasId(element.implClass, id);
                }
            }).all(action);
        }
    }
}

