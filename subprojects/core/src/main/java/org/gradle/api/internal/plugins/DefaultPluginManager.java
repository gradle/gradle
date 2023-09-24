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
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.code.DefaultUserCodeSource;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationId;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@NotThreadSafe
public class DefaultPluginManager implements PluginManagerInternal {

    public static final String CORE_PLUGIN_NAMESPACE = "org" + DefaultPluginId.SEPARATOR + "gradle";
    public static final String CORE_PLUGIN_PREFIX = CORE_PLUGIN_NAMESPACE + DefaultPluginId.SEPARATOR;

    private final Instantiator instantiator;
    private final PluginTarget target;
    private final PluginRegistry pluginRegistry;
    private final DefaultPluginContainer pluginContainer;
    private final Map<Class<?>, PluginImplementation<?>> plugins = Maps.newHashMap();
    private final Map<Class<?>, Plugin> instances = Maps.newLinkedHashMap();
    private final Map<PluginId, DomainObjectSet<PluginWithId>> idMappings = Maps.newHashMap();

    private final BuildOperationExecutor buildOperationExecutor;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private DomainObjectCollectionFactory domainObjectCollectionFactory;

    public DefaultPluginManager(final PluginRegistry pluginRegistry, Instantiator instantiator, final PluginTarget target, BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext, CollectionCallbackActionDecorator callbackDecorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.instantiator = instantiator;
        this.target = target;
        this.pluginRegistry = pluginRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.pluginContainer = new DefaultPluginContainer(pluginRegistry, this, callbackDecorator);
        this.buildOperationExecutor = buildOperationExecutor;
        this.userCodeApplicationContext = userCodeApplicationContext;
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

    @Override
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

    @Override
    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }

    @Override
    public <P extends Plugin<?>> Optional<PluginId> findPluginIdForClass(Class<P> plugin) {
        PluginImplementation<?> pluginImplementation = plugins.get(plugin);
        if (pluginImplementation != null) {
            return Optional.ofNullable(pluginImplementation.getPluginId());
        }
        return Optional.empty();
    }

    @Override
    public void apply(PluginImplementation<?> plugin) {
        doApply(plugin);
    }

    @Override
    public void apply(String pluginId) {
        PluginImplementation<?> plugin = pluginRegistry.lookup(DefaultPluginId.unvalidated(pluginId));
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }
        doApply(plugin);
    }

    @Override
    public void apply(Class<?> type) {
        doApply(pluginRegistry.inspect(type));
    }

    private void doApply(final PluginImplementation<?> plugin) {
        PluginId pluginId = plugin.getPluginId();
        final String pluginIdStr = pluginId == null ? null : pluginId.toString();
        final Class<?> pluginClass = plugin.asClass();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(pluginClass.getClassLoader());
            if (plugin.getType().equals(PotentialPlugin.Type.UNKNOWN)) {
                throw new InvalidPluginException("'" + pluginClass.getName() + "' is neither a plugin or a rule source and cannot be applied.");
            } else {
                final Runnable adder = addPluginInternal(plugin);
                if (adder != null) {
                    UserCodeSource source = new DefaultUserCodeSource(plugin.getDisplayName(), pluginIdStr);
                    userCodeApplicationContext.apply(source, userCodeApplicationId ->
                        buildOperationExecutor.run(new AddPluginBuildOperation(adder, plugin, pluginIdStr, pluginClass, userCodeApplicationId)));
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

    private void addPlugin(Runnable adder, PluginImplementation<?> plugin, String pluginId, Class<?> pluginClass) {
        boolean imperative = plugin.isImperative();
        if (imperative) {
            Plugin<?> pluginInstance = producePluginInstance(pluginClass);

            if (plugin.isHasRules()) {
                target.applyImperativeRulesHybrid(pluginId, pluginInstance, pluginClass);
            } else {
                target.applyImperative(pluginId, pluginInstance);
            }

            // Important not to add until after it has been applied as there can be
            // plugins.withType() callbacks waiting to build on what the plugin did
            instances.put(pluginClass, pluginInstance);
            pluginContainer.pluginAdded(pluginInstance);
        } else {
            target.applyRules(pluginId, pluginClass);
        }

        adder.run();
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

    @Override
    public DomainObjectSet<PluginWithId> pluginsForId(String id) {
        PluginId pluginId = DefaultPluginId.unvalidated(id);
        DomainObjectSet<PluginWithId> pluginsForId = idMappings.get(pluginId);
        if (pluginsForId == null) {
            pluginsForId = domainObjectCollectionFactory.newDomainObjectSet(PluginWithId.class);
            idMappings.put(pluginId, pluginsForId);
            for (PluginImplementation<?> plugin : plugins.values()) {
                if (plugin.isAlsoKnownAs(pluginId)) {
                    pluginsForId.add(new PluginWithId(pluginId, plugin.asClass()));
                }
            }
        }

        return pluginsForId;
    }

    @Override
    public AppliedPlugin findPlugin(final String id) {
        DomainObjectSet<PluginWithId> pluginWithIds = pluginsForId(id);
        if (!pluginWithIds.isEmpty()) {
            return pluginWithIds.iterator().next().asAppliedPlugin();
        }
        return null;
    }

    @Override
    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    @Override
    public void withPlugin(final String id, final Action<? super AppliedPlugin> action) {
        Action<PluginWithId> wrappedAction = new Action<PluginWithId>() {
            @Override
            public void execute(PluginWithId pluginWithId) {
                action.execute(pluginWithId.asAppliedPlugin());
            }
        };
        pluginsForId(id).all(wrappedAction);
    }

    private class AddPluginBuildOperation implements RunnableBuildOperation {

        private final Runnable adder;
        private final PluginImplementation<?> plugin;
        private final String pluginId;
        private final Class<?> pluginClass;
        private final UserCodeApplicationId applicationId;

        private AddPluginBuildOperation(Runnable adder, PluginImplementation<?> plugin, String pluginId, Class<?> pluginClass, UserCodeApplicationId applicationId) {
            this.adder = adder;
            this.plugin = plugin;
            this.pluginId = pluginId;
            this.pluginClass = pluginClass;
            this.applicationId = applicationId;
        }

        @Override
        public void run(BuildOperationContext context) {
            addPlugin(adder, plugin, pluginId, pluginClass);
            context.setResult(OPERATION_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return computeApplyPluginBuildOperationDetails(plugin);
        }

        private BuildOperationDescriptor.Builder computeApplyPluginBuildOperationDetails(final PluginImplementation<?> pluginImplementation) {
            String pluginIdentifier;
            if (pluginImplementation.getPluginId() != null) {
                pluginIdentifier = pluginImplementation.getPluginId().toString();
            } else {
                pluginIdentifier = pluginImplementation.asClass().getName();
            }
            String name = "Apply plugin " + pluginIdentifier;
            return BuildOperationDescriptor.displayName(name + " to " + target.toString())
                .name(name)
                .details(new OperationDetails(pluginImplementation, target.getConfigurationTargetIdentifier(), applicationId));
        }
    }

    private static class OperationDetails implements ApplyPluginBuildOperationType.Details, CustomOperationTraceSerialization {

        private final PluginImplementation<?> pluginImplementation;
        private final ConfigurationTargetIdentifier targetIdentifier;
        private final UserCodeApplicationId applicationId;

        private OperationDetails(PluginImplementation<?> pluginImplementation, ConfigurationTargetIdentifier targetIdentifier, UserCodeApplicationId applicationId) {
            this.pluginImplementation = pluginImplementation;
            this.targetIdentifier = targetIdentifier;
            this.applicationId = applicationId;
        }

        @Override
        @Nullable
        public String getPluginId() {
            PluginId pluginId = pluginImplementation.getPluginId();
            return pluginId == null ? null : pluginId.getId();
        }

        @Override
        public Class<?> getPluginClass() {
            return pluginImplementation.asClass();
        }

        @Override
        public String getTargetType() {
            return targetIdentifier.getTargetType().label;
        }

        @Nullable
        @Override
        public String getTargetPath() {
            return targetIdentifier.getTargetPath();
        }

        @Override
        public String getBuildPath() {
            return targetIdentifier.getBuildPath();
        }

        @Override
        public long getApplicationId() {
            return applicationId.longValue();
        }

        @Override
        public Object getCustomOperationTraceSerializableModel() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("pluginId", getPluginId());
            map.put("pluginClass", getPluginClass().getName());
            map.put("targetType", getTargetType());
            map.put("targetPath", getTargetPath());
            map.put("buildPath", getBuildPath());
            map.put("applicationId", getApplicationId());
            return map;
        }
    }

    private static final ApplyPluginBuildOperationType.Result OPERATION_RESULT = new ApplyPluginBuildOperationType.Result() {
    };
}

