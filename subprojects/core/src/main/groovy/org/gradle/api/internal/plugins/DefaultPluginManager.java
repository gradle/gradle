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
import org.gradle.api.plugins.*;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.plugin.internal.PluginId;

import java.util.Map;
import java.util.Set;

@NotThreadSafe
public class DefaultPluginManager implements PluginManagerInternal {

    public static final String CORE_PLUGIN_NAMESPACE = "org" + PluginId.SEPARATOR + "gradle";
    public static final String CORE_PLUGIN_PREFIX = CORE_PLUGIN_NAMESPACE + PluginId.SEPARATOR;

    private final Instantiator instantiator;
    private final PluginApplicator applicator;
    private final PluginRegistry pluginRegistry;
    private final DefaultPluginContainer pluginContainer;
    private final Set<Class<?>> plugins = Sets.newHashSet();
    private final Set<Plugin<?>> instances = Sets.newHashSet();
    private final Map<String, DomainObjectSet<PluginWithId>> idMappings = Maps.newHashMap();

    public DefaultPluginManager(final PluginRegistry pluginRegistry, Instantiator instantiator, final PluginApplicator applicator) {
        this.instantiator = instantiator;
        this.applicator = applicator;
        this.pluginRegistry = pluginRegistry;
        this.pluginContainer = new DefaultPluginContainer(pluginRegistry, this);
    }

    private <T> T instantiatePlugin(Class<T> type) {
        try {
            return instantiator.newInstance(type);
        } catch (ObjectInstantiationException e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.", type.getSimpleName()), e.getCause());
        }
    }

    public <P extends Plugin> P addImperativePlugin(String id, Class<P> type) {
        return doApply(id, pluginRegistry.inspect(type));
    }

    @Nullable
    public static String maybeQualify(String id) {
        if (id.startsWith(CORE_PLUGIN_PREFIX)) {
            return null;
        } else {
            return CORE_PLUGIN_PREFIX + id;
        }
    }

    private boolean addPluginInternal(Class<?> pluginClass) {
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

    @Nullable
    private <T> T doApply(@Nullable final String pluginId, PotentialPlugin<T> potentialPlugin) {
        Class<T> pluginClass = potentialPlugin.asClass();
        try {
            if (potentialPlugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
                throw new InvalidPluginException("'" + pluginClass.getName() + "' is neither a plugin or a rule source and cannot be applied.");
            } else {
                boolean imperative = potentialPlugin.isImperative();
                if (addPluginInternal(pluginClass)) {
                    if (imperative) {
                        // This insanity is needed for the case where someone calls pluginContainer.add(new SomePlugin())
                        // That is, the plugin container has the instance that we want, but we don't think (we can't know) it has been applied
                        T instance = findInstance(pluginClass, pluginContainer);
                        if (instance == null) {
                            instance = instantiatePlugin(pluginClass);
                        }

                        Plugin<?> cast = Cast.uncheckedCast(instance);
                        instances.add(cast);

                        if (potentialPlugin.isHasRules()) {
                            applicator.applyImperativeRulesHybrid(pluginId, cast);
                        } else {
                            applicator.applyImperative(pluginId, cast);
                        }

                        // Important not to add until after it has been applied as there can be
                        // plugins.withType() callbacks waiting to build on what the plugin did
                        pluginContainer.add(cast);
                        return instance;
                    } else {
                        applicator.applyRules(pluginId, pluginClass);
                    }
                } else {
                    if (imperative) {
                        T instance = findInstance(pluginClass, instances);
                        if (instance == null) {
                            throw new IllegalStateException("Plugin of type " + pluginClass.getName() + " has been applied, but an instance wasn't found in the plugin container");
                        } else {
                            return instance;
                        }
                    }
                }
            }
        } catch (PluginApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginApplicationException(pluginId == null ? "class '" + pluginClass.getName() + "'" : "id '" + pluginId + "'", e);
        }

        return null;
    }

    private <T> T findInstance(Class<T> clazz, Iterable<?> instances) {
        for (Object instance : instances) {
            if (instance.getClass().equals(clazz)) {
                return clazz.cast(instance);
            }
        }

        return null;
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

