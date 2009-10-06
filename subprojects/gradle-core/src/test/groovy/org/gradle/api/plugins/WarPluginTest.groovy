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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.tasks.bundling.War
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class WarPluginTest {
    private Project project // = HelperUtil.createRootProject()
    private WarPlugin warPlugin// = new WarPlugin()

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject()
        warPlugin = new WarPlugin()
    }

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
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.libExcludeConfigurations, equalTo([WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME, WarPlugin.WAR_TASK_NAME))
    }

    @Test public void dependsOnRuntimeConfig() {
        warPlugin.use(project, project.getPlugins())

        Project childProject = HelperUtil.createChildProject(project, 'child')
        JavaPlugin javaPlugin = new JavaPlugin()
        javaPlugin.use(childProject, childProject.getPlugins())

        project.dependencies {
            runtime project(path: childProject.path, configuration: 'archives')
        }

        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, hasItem(':child:jar'))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        warPlugin.use(project, project.getPlugins())

        def task = project.createTask('customWar', type: War)
        assertThat(task, dependsOn(hasItems(JavaPlugin.CLASSES_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.libExcludeConfigurations, equalTo([WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn(JavaPlugin.JAR_TASK_NAME, WarPlugin.WAR_TASK_NAME, 'customWar'))
    }

    @Test public void addsDefaultWarToArchiveConfiguration() {
        warPlugin.use(project, project.getPlugins())

        Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        assertThat(archiveConfiguration.getAllArtifacts().size(), equalTo(1)); 
        assertThat(archiveConfiguration.getAllArtifacts().iterator().next().getType(), equalTo("war")); 
    }
}
