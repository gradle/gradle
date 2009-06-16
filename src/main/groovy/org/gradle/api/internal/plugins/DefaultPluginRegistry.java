/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.io.File;

/**
 * @author Hans Dockter
 */

public class DefaultPluginRegistry extends AbstractPluginContainer implements PluginRegistry {
    private static Logger logger = LoggerFactory.getLogger(DefaultPluginRegistry.class);

    private Properties properties = new Properties();

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

    public DefaultPluginRegistry() {
        super(Plugin.class);
    }

    public DefaultPluginRegistry(File pluginProperties) {
        super(Plugin.class);
        if (!pluginProperties.isFile()) {
            return;
        }
        logger.debug("Checking file= {}", pluginProperties);
        properties = GUtil.loadProperties(pluginProperties);
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
        for (Object o : properties.keySet()) {
            if (properties.getProperty(o.toString()).equals(type.getName())) {
                return o.toString();
            }
        }
        return null;
    }

    public Class<? extends Plugin> getTypeForId(String pluginId) {
        if (!GUtil.isTrue(properties.getProperty(pluginId))) {
            throw new UnknownPluginException("The plugin with the id " + pluginId + " is not available.");
        }
        try {
            return Class.forName(properties.getProperty(pluginId)).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new PluginInstantiationException(e);
        }
    }

}