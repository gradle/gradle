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
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.WarPluginConvention
import org.gradle.api.tasks.bundling.War
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
// todo Make test stronger
class WarPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final WarPlugin warPlugin = new WarPlugin()

    @Test public void appliesJavaPluginAndAddsConvention() {
        warPlugin.use(project, project.getPlugins())

        assertTrue(project.getPlugins().hasPlugin(JavaPlugin));
        assertThat(project.convention.plugins.war, instanceOf(WarPluginConvention))
    }
    
    @Test public void createsConfigurations() {
        warPlugin.use(project, project.getPlugins())

        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME, WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void addsTasks() {
        warPlugin.use(project, project.getPlugins())

        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        assertThat(task, instanceOf(War))
        assertDependsOn(task, JavaPlugin.TEST_TASK_NAME,
                              JavaPlugin.COMPILE_TASK_NAME,
                              JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.libExcludeConfigurations, equalTo([WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]))

        task = project.tasks[JavaPlugin.LIBS_TASK_NAME]
        assertDependsOn(task, JavaPlugin.JAR_TASK_NAME, WarPlugin.WAR_TASK_NAME)
    }

    @Test public void appliesMappingsToArchiveTasks() {
        warPlugin.use(project, project.getPlugins())

        def task = project.createTask('customWar', type: War)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.TEST_TASK_NAME, JavaPlugin.COMPILE_TASK_NAME,
                              JavaPlugin.PROCESS_RESOURCES_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.libExcludeConfigurations, equalTo([WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]))

        assertDependsOn(project.tasks[JavaPlugin.LIBS_TASK_NAME], JavaPlugin.JAR_TASK_NAME, WarPlugin.WAR_TASK_NAME, 'customWar')
    }

    @Test public void addsDefaultWarToArchiveConfiguration() {
        warPlugin.use(project, project.getPlugins())

        Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        assertThat(archiveConfiguration.getAllArtifacts().size(), equalTo(1)); 
        assertThat(archiveConfiguration.getAllArtifacts().iterator().next().getType(), equalTo("war")); 
    }

    private void assertDependsOn(Task task, String... names) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, equalTo(toSet(names)))
    }

}
