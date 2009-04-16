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
 
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.util.HelperUtil
import org.gradle.util.WrapUtil
import org.hamcrest.Matchers
import org.junit.Test
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class WarPluginTest {
    @Test public void testApply() {
        // todo Make test stronger
        Project project = HelperUtil.createRootProject()
        WarPlugin warPlugin = new WarPlugin()
        warPlugin.apply(project, new PluginRegistry(), [:])

        assertTrue(project.getAppliedPlugins().contains(JavaPlugin));

        def configuration = project.configurations.get(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.get(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME, WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }
}
