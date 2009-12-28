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

import org.gradle.api.Plugin;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;
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

public class DefaultPluginRegistry extends AbstractPluginContainer implements PluginRegistry {
    private static Logger logger = LoggerFactory.getLogger(DefaultPluginRegistry.class);
    private final Map<String, String> properties = new HashMap<String, String>();

    private PluginProvider pluginProvider = new PluginProvider() {
        public Plugin providePlugin(Class<? extends Plugin> type) {
            try {
                return type.newInstance();
            } catch (IllegalAccessException e) {
                throw new PluginInstantiationException(e);
            } catch (InstantiationException e) {
                throw new PluginInstantiationException(e);
            }
        }
    };

    public DefaultPluginRegistry(ClassLoader classLoader) {
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

    public Plugin loadPlugin(String pluginId) {
        return addPlugin(pluginId, pluginProvider);
    }

    public <T extends Plugin> T loadPlugin(Class<T> pluginClass) {
        return addPlugin(pluginClass, pluginProvider);
    }

    public String getNameForType(Class<? extends Plugin> type) {
        return GUtil.elvis(getIdForType(type), type.getName());
    }

    public String getIdForType(Class<? extends Plugin> type) {
        for (String id : properties.keySet()) {
            if (properties.get(id).equals(type.getName())) {
                return id;
            }
        }
        return null;
    }

    public Class<? extends Plugin> getTypeForId(String pluginId) {
        if (!GUtil.isTrue(properties.get(pluginId))) {
            throw new UnknownPluginException("The plugin with the id '" + pluginId + "' is not available.");
        }
        try {
            return Class.forName(properties.get(pluginId)).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new PluginInstantiationException(e);
        }
    }

}