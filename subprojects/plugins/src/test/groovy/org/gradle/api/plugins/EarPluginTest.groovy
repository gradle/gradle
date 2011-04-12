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
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.tasks.bundling.Ear
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author David Gileadi
 */
class EarPluginTest {
    private Project project
    private EarPlugin earPlugin

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject()
        earPlugin = new EarPlugin()
    }

    @Test public void appliesBasePluginAndAddsConvention() {
        earPlugin.apply(project)
        
        assertTrue(project.getPlugins().hasPlugin(BasePlugin));
        assertThat(project.convention.plugins.ear, instanceOf(EarPluginConvention))
    }
    
    @Test public void createsConfigurations() {
        earPlugin.apply(project)

        def configuration = project.configurations.getByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(EarPlugin.EARLIB_CONFIGURATION_NAME)
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void addsTasks() {
        earPlugin.apply(project)

        def task = project.tasks[EarPlugin.EAR_TASK_NAME]
        assertThat(task, instanceOf(Ear))
        assertThat(task.destinationDir, equalTo(project.libsDir))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(EarPlugin.EAR_TASK_NAME))
    }

    @Test public void addsTasksToJavaProject() {
        project.plugins.apply(JavaPlugin.class)
        earPlugin.apply(project)

        def task = project.tasks[EarPlugin.EAR_TASK_NAME]
        assertThat(task, instanceOf(Ear))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME, EarPlugin.EAR_TASK_NAME))
    }

    @Test public void dependsOnEarlibConfig() {
        earPlugin.apply(project)

        Project childProject = HelperUtil.createChildProject(project, 'child')
        JavaPlugin javaPlugin = new JavaPlugin()
        javaPlugin.apply(childProject)

        project.dependencies {
            earlib project(path: childProject.path, configuration: 'archives')
        }

        def task = project.tasks[EarPlugin.EAR_TASK_NAME]
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, hasItem(':child:jar'))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        earPlugin.apply(project)

        def task = project.task(type: Ear, 'customEar')
        assertThat(task.destinationDir, equalTo(project.libsDir))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn(EarPlugin.EAR_TASK_NAME, 'customEar'))
    }

    @Test public void appliesMappingsToArchiveTasksForJavaProject() {
        project.plugins.apply(JavaPlugin.class)
        earPlugin.apply(project)

        def task = project.task(type: Ear, 'customEar')
        assertThat(task, dependsOn(hasItems(JavaPlugin.CLASSES_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.libsDir))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn(JavaPlugin.JAR_TASK_NAME, EarPlugin.EAR_TASK_NAME, 'customEar'))
    }

    @Test public void addsDefaultEarToArchiveConfiguration() {
        earPlugin.apply(project)

        Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        assertThat(archiveConfiguration.getAllArtifacts().size(), equalTo(1));
        assertThat(archiveConfiguration.getAllArtifacts().iterator().next().getType(), equalTo("ear"));
    }

    @Test public void supportsAppDir() {
        project.file("src/main/application/META-INF").mkdirs()
        project.file("src/main/application/META-INF/test.txt").createNewFile()
        project.file("src/main/application/test2.txt").createNewFile()

        earPlugin.apply(project)

        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        def ear = project.zipTree("build/libs/${project.name}.ear")
        assertFalse ear.isEmpty()
        assertNotNull ear.files.find { it.name == "test2.txt" }
        assertNotNull ear.files.find { it.name == "test.txt" }
    }

    private void execute(Task task) {
        for (Task dep : task.taskDependencies.getDependencies(task)) {
            for (Action action : dep.actions) {
                action.execute(dep)
            }
        }
        for (Action action : task.actions) {
            action.execute(task)
        }
    }
}
