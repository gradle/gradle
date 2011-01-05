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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */

public class DefaultPluginRegistry implements PluginRegistry {
    private final Map<String, Class<? extends Plugin>> idMappings = new HashMap<String, Class<? extends Plugin>>();
    private final DefaultPluginRegistry parent;
    private final ClassLoader classLoader;

    public DefaultPluginRegistry(ClassLoader classLoader) {
        this(null, classLoader);
    }

    private DefaultPluginRegistry(DefaultPluginRegistry parent, ClassLoader classLoader) {
        this.parent = parent;
        this.classLoader = classLoader;
    }

    public PluginRegistry createChild(ClassLoader childClassPath) {
        return new DefaultPluginRegistry(this, childClassPath);
    }

    public <T extends Plugin> T loadPlugin(Class<T> pluginClass) {
        if (parent != null) {
            return parent.loadPlugin(pluginClass);
        }

        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            throw new InvalidUserDataException(String.format(
                    "Cannot create plugin of type '%s' as it does not implement the Plugin interface.",
                    pluginClass.getSimpleName()));
        }
        try {
            return pluginClass.newInstance();
        } catch (InstantiationException e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.",
                    pluginClass.getSimpleName()), e.getCause());
        } catch (Exception e) {
            throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.",
                    pluginClass.getSimpleName()), e);
        }
    }

    public Class<? extends Plugin> getTypeForId(String pluginId) {
        if (parent != null) {
            try {
                return parent.getTypeForId(pluginId);
            } catch (UnknownPluginException e) {
                // Ignore
            }
        }

        Class<? extends Plugin> implClass = idMappings.get(pluginId);
        if (implClass != null) {
            return implClass;
        }

        URL resource = classLoader.getResource(String.format("META-INF/gradle-plugins/%s.properties", pluginId));
        if (resource == null) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }

        Properties properties = GUtil.loadProperties(resource);
        String implClassName = properties.getProperty("implementation-class");
        if (!GUtil.isTrue(implClassName)) {
            throw new PluginInstantiationException(String.format(
                    "No implementation class specified for plugin '%s' in %s.", pluginId, resource));
        }

        try {
            implClass = classLoader.loadClass(implClassName).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new PluginInstantiationException(String.format(
                    "Could not find implementation class '%s' for plugin '%s' specified in %s.", implClass, pluginId,
                    resource), e);
        }

        idMappings.put(pluginId, implClass);
        return implClass;
    }
}