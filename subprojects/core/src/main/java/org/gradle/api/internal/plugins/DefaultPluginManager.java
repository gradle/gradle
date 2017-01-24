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

import com.google.common.collect.Lists;
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

@NotThreadSafe
public class DefaultPluginManager implements PluginManagerInternal {

    public static final String CORE_PLUGIN_NAMESPACE = "org" + PluginId.SEPARATOR + "gradle";
    public static final String CORE_PLUGIN_PREFIX = CORE_PLUGIN_NAMESPACE + PluginId.SEPARATOR;

    private final Instantiator instantiator;
    private final PluginApplicator applicator;
    private final PluginRegistry pluginRegistry;
    private final DefaultPluginContainer pluginContainer;
    private final Map<Class<?>, PluginImplementation<?>> plugins = Maps.newHashMap();
    private final Map<Class<?>, Plugin<?>> instances = Maps.newHashMap();
    private final Map<PluginId, DomainObjectSet<PluginWithId>> idMappings = Maps.newHashMap();

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

    @Override
    public <P extends Plugin> P addImperativePlugin(PluginImplementation<P> plugin) {
        doApply(plugin);
        Class<? extends P> pluginClass = plugin.asClass();
        return pluginClass.cast(instances.get(pluginClass));
    }

    public <P extends Plugin> P addImperativePlugin(Class<P> type) {
        return addImperativePlugin(pluginRegistry.inspect(type));
    }

    @Nullable // if the plugin has already been added
    private Runnable addPluginInternal(final PluginImplementation<?> plugin) {
        final Class<?> pluginClass = plugin.asClass();
        if (plugins.containsKey(pluginClass)) {
            return null;
        }

        plugins.put(pluginClass, plugin);
        return new Runnable() {
            @Override
            public void run() {
                // Take a copy because adding to an idMappings value may result in new mappings being added (i.e. ConcurrentModificationException)
                Iterable<PluginId> pluginIds = Lists.newArrayList(idMappings.keySet());
                for (PluginId id : pluginIds) {
                    if (plugin.isAlsoKnownAs(id)) {
                        idMappings.get(id).add(new PluginWithId(id, pluginClass));
                    }
                }
            }
        };
    }

    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }

    @Override
    public void apply(PluginImplementation<?> plugin) {
        doApply(plugin);
    }

    public void apply(String pluginId) {
        PluginImplementation<?> plugin = pluginRegistry.lookup(PluginId.unvalidated(pluginId));
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }
        doApply(plugin);
    }

    public void apply(Class<?> type) {
        doApply(pluginRegistry.inspect(type));
    }

    private void doApply(PluginImplementation<?> plugin) {
        PluginId pluginId = plugin.getPluginId();
        String pluginIdStr = pluginId == null ? null : pluginId.toString();
        Class<?> pluginClass = plugin.asClass();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(pluginClass.getClassLoader());
            if (plugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
                throw new InvalidPluginException("'" + pluginClass.getName() + "' is neither a plugin or a rule source and cannot be applied.");
            } else {
                boolean imperative = plugin.isImperative();
                Runnable adder = addPluginInternal(plugin);
                if (adder != null) {
                    if (imperative) {
                        Plugin<?> pluginInstance = producePluginInstance(pluginClass);
                        instances.put(pluginClass, pluginInstance);

                        if (plugin.isHasRules()) {
                            applicator.applyImperativeRulesHybrid(pluginIdStr, pluginInstance);
                        } else {
                            applicator.applyImperative(pluginIdStr, pluginInstance);
                        }

                        // Important not to add until after it has been applied as there can be
                        // plugins.withType() callbacks waiting to build on what the plugin did
                        pluginContainer.add(pluginInstance);
                    } else {
                        applicator.applyRules(pluginIdStr, pluginClass);
                    }

                    adder.run();
                }
            }
        } catch (PluginApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginApplicationException(plugin.getDisplayName(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private Plugin<?> producePluginInstance(Class<?> pluginClass) {
        // This insanity is needed for the case where someone calls pluginContainer.add(new SomePlugin())
        // That is, the plugin container has the instance that we want, but we don't think (we can't know) it has been applied
        Object instance = findInstance(pluginClass, pluginContainer);
        if (instance == null) {
            instance = instantiatePlugin(pluginClass);
        }

        return Cast.uncheckedCast(instance);
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
        PluginId pluginId = PluginId.unvalidated(id);
        DomainObjectSet<PluginWithId> pluginsForId = idMappings.get(pluginId);
        if (pluginsForId == null) {
            pluginsForId = new DefaultDomainObjectSet<PluginWithId>(PluginWithId.class, Sets.<PluginWithId>newLinkedHashSet());
            idMappings.put(pluginId, pluginsForId);
            for (PluginImplementation<?> plugin : plugins.values()) {
                if (plugin.isAlsoKnownAs(pluginId)) {
                    pluginsForId.add(new PluginWithId(pluginId, plugin.asClass()));
                }
            }
        }

        return pluginsForId;
    }

    public AppliedPlugin findPlugin(final String id) {
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
        pluginsForId(id).all(wrappedAction);
    }

}

