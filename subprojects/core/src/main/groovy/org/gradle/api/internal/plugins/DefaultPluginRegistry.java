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

import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.util.GUtil;

import java.util.Map;

public class DefaultPluginRegistry implements PluginRegistry {
    private final Map<String, Class<?>> idMappings = Maps.newHashMap();
    private final DefaultPluginRegistry parent;
    private final Factory<? extends ClassLoader> classLoaderFactory;
    private final Instantiator instantiator;

    public DefaultPluginRegistry(ClassLoader classLoader, Instantiator instantiator) {
        this(Factories.constant(classLoader), instantiator);
    }

    public DefaultPluginRegistry(Factory<? extends ClassLoader> classLoaderFactory, Instantiator instantiator) {
        this(null, classLoaderFactory, instantiator);
    }

    private DefaultPluginRegistry(DefaultPluginRegistry parent, Factory<? extends ClassLoader> classLoaderFactory, Instantiator instantiator) {
        this.parent = parent;
        this.classLoaderFactory = classLoaderFactory;
        this.instantiator = instantiator;
    }

    public PluginRegistry createChild(final ClassLoaderScope lookupScope, Instantiator instantiator) {
        Factory<ClassLoader> classLoaderFactory = new Factory<ClassLoader>() {
            public ClassLoader create() {
                return lookupScope.getLocalClassLoader();
            }
        };
        return new DefaultPluginRegistry(this, classLoaderFactory, instantiator);
    }

    public <T extends Plugin<?>> T loadPlugin(Class<T> pluginClass) {
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            throw new InvalidUserDataException(String.format(
                    "Cannot create plugin of type '%s' as it does not implement the Plugin interface.",
                    pluginClass.getSimpleName()));
        }
        try {
            return instantiator.newInstance(pluginClass);
        } catch (ObjectInstantiationException e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.",
                    pluginClass.getSimpleName()), e.getCause());
        }
    }

    protected Class<?> getTypeForId(String pluginId, Spec<Class<?>> classSpec, String exceptionMessageFormat) {
        if (parent != null) {
            try {
                return parent.getTypeForId(pluginId, classSpec, exceptionMessageFormat);
            } catch (UnknownPluginException e) {
                // Ignore
            }
        }

        Class<?> implClass = idMappings.get(pluginId);
        if (implClass != null) {
            if (!classSpec.isSatisfiedBy(implClass)) {
                throw new PluginInstantiationException(String.format(exceptionMessageFormat, implClass.getName(), pluginId));
            }
            return implClass;
        }

        ClassLoader classLoader = this.classLoaderFactory.create();

        PluginDescriptor pluginDescriptor = findPluginDescriptor(pluginId, classLoader);
        if (pluginDescriptor == null) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }

        String implClassName = pluginDescriptor.getImplementationClassName();
        if (!GUtil.isTrue(implClassName)) {
            throw new PluginInstantiationException(String.format(
                    "No implementation class specified for plugin '%s' in %s.", pluginId, pluginDescriptor));
        }

        try {
            implClass = classLoader.loadClass(implClassName);
            if (!classSpec.isSatisfiedBy(implClass)) {
                throw new PluginInstantiationException(String.format("%s Specified in %s.",
                        String.format(exceptionMessageFormat, implClassName, pluginId), pluginDescriptor));
            }
        } catch (ClassNotFoundException e) {
            throw new PluginInstantiationException(String.format(
                    "Could not find implementation class '%s' for plugin '%s' specified in %s.", implClassName, pluginId,
                    pluginDescriptor), e);
        }

        idMappings.put(pluginId, implClass);
        return implClass;
    }

    public Class<? extends Plugin<?>> getPluginTypeForId(String pluginId) {
        @SuppressWarnings("unchecked") Class<? extends Plugin<?>> pluginType = (Class<? extends Plugin<?>>) getTypeForId(pluginId, new Spec<Class<?>>() {
            public boolean isSatisfiedBy(Class<?> rawClass) {
                return Plugin.class.isAssignableFrom(rawClass);
            }
        }, "Implementation class '%s' specified for plugin '%s' does not implement the Plugin interface.");
        return pluginType;
    }

    public Class<?> getTypeForId(String pluginId) {
        return getTypeForId(pluginId, new Spec<Class<?>>() {
            public boolean isSatisfiedBy(Class<?> rawClass) {
                ModelRuleSourceDetector detector = new ModelRuleSourceDetector();
                return Plugin.class.isAssignableFrom(rawClass) || detector.getDeclaredSources(rawClass).size() > 0;
            }
        }, "Implementation class '%s' specified for plugin '%s' does not implement the Plugin interface and does not define any rule sources.");
    }

    protected PluginDescriptor findPluginDescriptor(String pluginId, ClassLoader classLoader) {
        PluginDescriptorLocator pluginDescriptorLocator = new ClassloaderBackedPluginDescriptorLocator(classLoader);
        return pluginDescriptorLocator.findPluginDescriptor(pluginId);
    }
}