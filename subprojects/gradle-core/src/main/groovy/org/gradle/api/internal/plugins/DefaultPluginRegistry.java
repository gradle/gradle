/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;
import org.gradle.util.ObservableUrlClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */

public class DefaultPluginRegistry implements PluginRegistry {
    private static Logger logger = LoggerFactory.getLogger(DefaultPluginRegistry.class);
    private final Map<String, String> properties = new HashMap<String, String>();
    private final Map<Class<? extends Plugin>, Plugin<?>> plugins = new HashMap<Class<? extends Plugin>, Plugin<?>>();
    private final DefaultPluginRegistry parent;
    private final ClassLoader classLoader;

    public DefaultPluginRegistry(ClassLoader classLoader) {
        this(null, classLoader);
    }

    private DefaultPluginRegistry(DefaultPluginRegistry parent, ClassLoader classLoader) {
        this.parent = parent;
        this.classLoader = classLoader;
        buildProperties(classLoader);
        if (classLoader instanceof ObservableUrlClassLoader) {
            ObservableUrlClassLoader observableUrlClassLoader = (ObservableUrlClassLoader) classLoader;
            observableUrlClassLoader.whenUrlAdded(new Action<ClassLoader>() {
                public void execute(ClassLoader classLoader) {
                    buildProperties(classLoader);
                }
            });
        }
    }

    private void buildProperties(ClassLoader classLoader) {
        properties.clear();
        try {
            Enumeration<URL> propertiesFiles = classLoader.getResources("META-INF/gradle-plugins.properties");
            while (propertiesFiles.hasMoreElements()) {
                URL url = propertiesFiles.nextElement();
                logger.debug("Adding plugins from {}", url);
                GUtil.addToMap(properties, GUtil.loadProperties(url));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PluginRegistry createChild(ClassLoader childClassPath) {
        return new DefaultPluginRegistry(this, childClassPath);
    }

    public Plugin loadPlugin(String pluginId) {
        return loadPlugin(getTypeForId(pluginId));
    }

    public <T extends Plugin> T loadPlugin(Class<T> pluginClass) {
        if (parent != null) {
            return parent.loadPlugin(pluginClass);
        }
        
        T plugin = pluginClass.cast(plugins.get(pluginClass));
        if (plugin == null) {
            try {
                plugin = pluginClass.newInstance();
            } catch (InstantiationException e) {
                throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.",
                        pluginClass.getSimpleName()), e.getCause());
            } catch (Exception e) {
                throw new PluginInstantiationException(String.format("Could not create plugin of type '%s'.",
                        pluginClass.getSimpleName()), e);
            }
            plugins.put(pluginClass, plugin);
        }
        return plugin;
    }

    public String getNameForType(Class<? extends Plugin> type) {
        return GUtil.elvis(getIdForType(type), type.getName());
    }

    public String getIdForType(Class<? extends Plugin> type) {
        if (parent != null) {
            String id = parent.getIdForType(type);
            if (id != null) {
                return id;
            }
        }
        
        for (String id : properties.keySet()) {
            if (properties.get(id).equals(type.getName())) {
                return id;
            }
        }
        return null;
    }

    public Class<? extends Plugin> getTypeForId(String pluginId) {
        if (parent != null) {
            try {
                return parent.getTypeForId(pluginId);
            } catch (UnknownPluginException e) {
                // Ignore
            }
        }

        String implClassName = properties.get(pluginId);
        if (!GUtil.isTrue(implClassName)) {
            throw new UnknownPluginException("Plugin with id '" + pluginId + "' not found.");
        }
        
        try {
            return classLoader.loadClass(implClassName).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new PluginInstantiationException(String.format("Could not find implementation class '%s' for plugin '%s'.", implClassName, pluginId), e);
        }
    }
}