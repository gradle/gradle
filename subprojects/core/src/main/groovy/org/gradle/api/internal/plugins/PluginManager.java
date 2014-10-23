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

import com.google.common.collect.Maps;
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
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;
import org.gradle.util.SingleMessageLogger;

import java.util.Map;
import java.util.Set;

@NotThreadSafe
public class PluginManager {

    public static final String CORE_PLUGIN_NAMESPACE = "org" + PluginId.SEPARATOR + "gradle";
    public static final String CORE_PLUGIN_PREFIX = CORE_PLUGIN_NAMESPACE + PluginId.SEPARATOR;

    private final PluginApplicator applicator;
    private final PluginRegistry pluginRegistry;
    private final DefaultPluginContainer pluginContainer;
    private final Set<Class<?>> plugins = Sets.newHashSet();
    private final Map<String, DomainObjectSet<PluginWithId>> idMappings = Maps.newHashMap();

    public PluginManager(PluginRegistry pluginRegistry, Instantiator instantiator, final PluginApplicator applicator) {
        this.applicator = applicator;
        this.pluginRegistry = pluginRegistry;
        this.pluginContainer = new DefaultPluginContainer(pluginRegistry, this, instantiator, new PluginApplicator() {
            public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
                if (addPluginDirect(plugin.getClass())) {
                    applicator.applyImperative(pluginId, plugin);
                }
            }

            public void applyRules(@Nullable String pluginId, Class<?> clazz) {
                // plugin container should never try to apply such plugins
                throw new UnsupportedOperationException();
            }

            public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin) {
                if (addPluginDirect(plugin.getClass())) {
                    applicator.applyImperativeRulesHybrid(pluginId, plugin);
                }
            }
        });
    }

    @Nullable
    public static String maybeQualify(String id) {
        if (id.startsWith(CORE_PLUGIN_PREFIX)) {
            return null;
        } else {
            return CORE_PLUGIN_PREFIX + id;
        }
    }

    public boolean addPluginDirect(Class<?> pluginClass) {
        boolean added = plugins.add(pluginClass);
        if (added) {
            for (String id : idMappings.keySet()) {
                if (hasId(pluginClass, id)) {
                    idMappings.get(id).add(new PluginWithId(id, pluginClass));
                }
            }
        }
        return added;
    }

    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }

    public void apply(String pluginId) {
        PotentialPluginWithId potentialPluginWithId = pluginRegistry.lookup(pluginId);
        if (potentialPluginWithId == null) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }
        doApply(potentialPluginWithId.getPluginId().toString(), potentialPluginWithId);
    }

    public void apply(Class<?> type) {
        PotentialPlugin potentialPlugin = pluginRegistry.inspect(type);
        doApply(null, potentialPlugin);
    }

    private void doApply(@Nullable final String pluginId, PotentialPlugin potentialPlugin) {
        Class<?> pluginClass = potentialPlugin.asClass();
        try {
            if (potentialPlugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
                throw new InvalidPluginException("'" + pluginClass.getName() + "' is neither a plugin or a rule source and cannot be applied.");
            } else {
                final Class<? extends Plugin<?>> asImperativeClass = potentialPlugin.asImperativeClass();
                if (asImperativeClass == null) {
                    if (addPluginDirect(pluginClass)) {
                        applicator.applyRules(pluginId, pluginClass);
                    }
                } else {
                    SingleMessageLogger.whileDisabled(new Runnable() {
                        public void run() {
                            if (pluginId == null) {
                                pluginContainer.apply(asImperativeClass);
                            } else {
                                pluginContainer.apply(pluginId);
                            }
                        }
                    });
                }

            }
        } catch (PluginApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginApplicationException(pluginId == null ? "class '" + pluginClass.getName() + "'" : "id '" + pluginId + "'", e);
        }
    }

    public class PluginWithId {
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

    public DomainObjectSet<PluginWithId> pluginsForId(String id) {
        DomainObjectSet<PluginWithId> pluginsForId = idMappings.get(id);
        if (pluginsForId == null) {
            pluginsForId = new DefaultDomainObjectSet<PluginWithId>(PluginWithId.class, Sets.<PluginWithId>newLinkedHashSet());
            idMappings.put(id, pluginsForId);
            for (Class<?> plugin : plugins) {
                if (hasId(plugin, id)) {
                    pluginsForId.add(new PluginWithId(id, plugin));
                }
            }
        }

        return pluginsForId;
    }

    private boolean hasId(Class<?> plugin, String id) {
        PotentialPluginWithId potentialPluginWithId = pluginRegistry.lookup(id, plugin.getClassLoader());
        return potentialPluginWithId != null && potentialPluginWithId.getPluginId().toString().equals(id) && potentialPluginWithId.asClass().equals(plugin);
    }

    public AppliedPlugin findPlugin(final String id) {
        String qualified = maybeQualify(id);
        if (qualified != null) {
            DomainObjectSet<PluginWithId> pluginWithIds = pluginsForId(qualified);
            if (!pluginWithIds.isEmpty()) {
                return pluginWithIds.iterator().next().asAppliedPlugin();
            }
        }

        DomainObjectSet<PluginWithId> pluginWithIds = pluginsForId(id);
        if (!pluginWithIds.isEmpty()) {
            return pluginWithIds.iterator().next().asAppliedPlugin();
        }

        return null;
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public void withPlugin(final String id, final Action<? super AppliedPlugin> action) {
        Action<PluginWithId> wrappedAction = new Action<PluginWithId>() {
            public void execute(PluginWithId pluginWithId) {
                action.execute(pluginWithId.asAppliedPlugin());
            }
        };

        String qualified = maybeQualify(id);
        if (qualified != null) {
            pluginsForId(qualified).all(wrappedAction);
        }

        pluginsForId(id).all(wrappedAction);
    }

}

