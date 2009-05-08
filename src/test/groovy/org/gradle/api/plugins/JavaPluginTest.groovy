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
 
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class JavaPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaPlugin javaPlugin = new JavaPlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaPlugin.apply(project, new PluginRegistry())

        assertTrue(project.appliedPlugins.contains(ReportingBasePlugin))
        assertTrue(project.appliedPlugins.contains(BasePlugin))

        assertThat(project.convention.plugins.java, instanceOf(JavaPluginConvention))
    }

    @Test public void createsConfigurations() {
        javaPlugin.apply(project, new PluginRegistry())

        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(Dependency.MASTER_CONFIGURATION, JavaPlugin.RUNTIME_CONFIGURATION_NAME)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.MASTER_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.DISTS_TASK_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void createsTasksAndAppliesMappings() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.task(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task, instanceOf(Copy))
        assertDependsOn(task, JavaPlugin.INIT_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.COMPILE_TASK_NAME)
        assertThat(task, instanceOf(Compile))
        assertDependsOn(task, JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME)
        assertThat(task, instanceOf(Copy))
        assertDependsOn(task, JavaPlugin.COMPILE_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.COMPILE_TESTS_TASK_NAME)
        assertThat(task, instanceOf(Compile))
        assertDependsOn(task, JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.TEST_TASK_NAME)
        assertThat(task, instanceOf(org.gradle.api.tasks.testing.Test))
        assertDependsOn(task, JavaPlugin.COMPILE_TESTS_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.JAR_TASK_NAME)
        assertThat(task, instanceOf(Jar))
        assertDependsOn(task, JavaPlugin.TEST_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.LIBS_TASK_NAME)
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.JAR_TASK_NAME)

        task = project.task(JavaPlugin.DISTS_TASK_NAME)
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.LIBS_TASK_NAME)

        task = project.task(JavaPlugin.JAVADOC_TASK_NAME)
        assertThat(task, instanceOf(Javadoc))
        assertDependsOn(task)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))

        task = project.task("buildMaster")
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.JAR_TASK_NAME)
    }

    @Test public void appliesMappingsToTasksDefinedByBuildScript() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.createTask('customResources', type: Copy)
        assertDependsOn(task, JavaPlugin.INIT_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customCompile', type: Compile)
        assertDependsOn(task, JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertDependsOn(task, JavaPlugin.COMPILE_TESTS_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertDependsOn(task)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.createTask('customJar', type: Jar)
        assertDependsOn(task, JavaPlugin.TEST_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.classesDir))

        assertDependsOn(project.task(JavaPlugin.LIBS_TASK_NAME), JavaPlugin.JAR_TASK_NAME, 'customJar')

        task = project.createTask('customZip', type: Zip)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.LIBS_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))

        assertDependsOn(project.task(JavaPlugin.DISTS_TASK_NAME), JavaPlugin.LIBS_TASK_NAME, 'customZip')

        task = project.createTask('customTar', type: Tar)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.LIBS_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))

        assertDependsOn(project.task(JavaPlugin.DISTS_TASK_NAME), JavaPlugin.LIBS_TASK_NAME, 'customZip', 'customTar')
    }

    private void assertDependsOn(Task task, String... names) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, equalTo(toSet(names)))
    }
}
