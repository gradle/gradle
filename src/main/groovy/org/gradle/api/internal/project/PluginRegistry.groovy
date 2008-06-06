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
package org.gradle.api.internal.project

import org.gradle.api.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.Project

/**
 * @author Hans Dockter
 */

class PluginRegistry {
    private static Logger logger = LoggerFactory.getLogger(PluginRegistry)

    private Properties properties = new Properties()

    private Map plugins = [:]

    PluginRegistry() {
    }

    PluginRegistry(File pluginProperties) {
        if (!pluginProperties.isFile()) { return }
        logger.debug("Checking file=$pluginProperties")
        properties = new Properties()
        properties.load(new FileInputStream(pluginProperties))
    }

    Plugin getPlugin(String pluginId) {
        if (!properties[pluginId]) { return null }
        getPlugin(properties[pluginId] as Class)
    }

    Plugin getPlugin(Class pluginClass) {
        if (!plugins[pluginClass]) {
            plugins[pluginClass] = pluginClass.newInstance()
        }
        plugins[pluginClass]
    }

    void apply(Class pluginClass, Project project, PluginRegistry pluginRegistry, Map customValues) {
        if (project.pluginApplyRegistry[pluginClass] == null) {
            getPlugin(pluginClass).apply(project, pluginRegistry, customValues)
            project.pluginApplyRegistry[pluginClass] = ''
        }
    }

}
