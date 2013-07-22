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
 
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.tasks.bundling.War
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test

import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class WarPluginTest {
    private Project project // = HelperUtil.createRootProject()
    private WarPlugin warPlugin// = new WarPlugin()

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject()
        warPlugin = new WarPlugin()
    }

    @Test public void appliesJavaPluginAndAddsConvention() {
        warPlugin.apply(project)

        assertTrue(project.getPlugins().hasPlugin(JavaPlugin));
        assertThat(project.convention.plugins.war, instanceOf(WarPluginConvention))
    }
    
    @Test public void createsConfigurations() {
        warPlugin.apply(project)

        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

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
        warPlugin.apply(project)

        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        assertThat(task, instanceOf(War))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(WarPlugin.WAR_TASK_NAME))
    }

    @Test public void dependsOnRuntimeConfig() {
        warPlugin.apply(project)

        Project childProject = HelperUtil.createChildProject(project, 'child')
        JavaPlugin javaPlugin = new JavaPlugin()
        javaPlugin.apply(childProject)

        project.dependencies {
            runtime project(path: childProject.path, configuration: 'archives')
        }

        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, hasItem(':child:jar'))
    }

    @Test public void usesRuntimeClasspathExcludingProvidedAsClasspath() {
        File compileJar = project.file('compile.jar')
        File runtimeJar = project.file('runtime.jar')
        File providedJar = project.file('provided.jar')

        warPlugin.apply(project)

        project.dependencies {
            providedCompile project.files(providedJar)
            compile project.files(compileJar)
            runtime project.files(runtimeJar)
        }

        def task = project.tasks[WarPlugin.WAR_TASK_NAME]
        assertThat(task.classpath.files as List, equalTo([project.sourceSets.main.output.classesDir, project.sourceSets.main.output.resourcesDir, runtimeJar, compileJar]))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        warPlugin.apply(project)

        def task = project.task('customWar', type: War)
        assertThat(task, dependsOn(hasItems(JavaPlugin.CLASSES_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.libsDir))
    }

    @Test public void replacesJarAsPublication() {
        warPlugin.apply(project)

        Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        assertThat(archiveConfiguration.getAllArtifacts().size(), equalTo(1)); 
        assertThat(archiveConfiguration.getAllArtifacts().iterator().next().getType(), equalTo("war")); 
    }
}
