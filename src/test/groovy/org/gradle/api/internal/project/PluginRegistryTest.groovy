/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.internal.project.PluginRegistry
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/

class PluginRegistryTest extends GroovyTestCase {
    void testGetPlugin() {
        Properties properties = new Properties()
        properties.putAll([plugin1: 'org.gradle.api.internal.project.TestPlugin1'])
        File testDir = HelperUtil.makeNewTestDir()
        File propertiesFile = new File(testDir, 'plugin.properties')
        properties.save(new FileOutputStream(propertiesFile), '')

        PluginRegistry pluginRegistry = new PluginRegistry(propertiesFile)
        TestPlugin1 testPlugin1 = pluginRegistry.getPlugin('plugin1')
        assert pluginRegistry.getPlugin('plugin1').is(testPlugin1)
        assert pluginRegistry.getPlugin(TestPlugin1).is(testPlugin1)
        
        assertTrue(pluginRegistry.getPlugin(TestPlugin2) instanceof TestPlugin2)

        assertNull(pluginRegistry.getPlugin('unknownId'))
    }

    void testGetPluginWithNonExistentPropertiesFile() {
        File propertiesFile = new File('/plugin.properties')
        assertFalse(propertiesFile.isFile())
        PluginRegistry pluginRegistry = new PluginRegistry(propertiesFile)
        assertTrue(pluginRegistry.getPlugin(TestPlugin2) instanceof TestPlugin2)
        assertNull(pluginRegistry.getPlugin('unknownId'))
    }

    void testGetPluginWithNoPropertiesFile() {
        PluginRegistry pluginRegistry = new PluginRegistry()
        assertTrue(pluginRegistry.getPlugin(TestPlugin2) instanceof TestPlugin2)
        assertNull(pluginRegistry.getPlugin('unknownId'))
    }


    void testApply() {
        PluginRegistry pluginRegistry = new PluginRegistry()
        DefaultProject project = [:] as DefaultProject
        pluginRegistry.apply(TestPlugin1, project, pluginRegistry, [:])
        pluginRegistry.apply(TestPlugin1, project, pluginRegistry, [:])
        assertEquals(1, pluginRegistry.getPlugin(TestPlugin1).applyCounter)
    }
}
