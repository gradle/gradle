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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.util.HelperUtil
import org.gradle.util.WrapUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.internal.DefaultTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
// todo Make test stronger
class JavaPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaPlugin javaPlugin = new JavaPlugin()

    @Test public void testApplyCreatesConfigurations() {
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
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.INIT_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.COMPILE_TASK_NAME)
        assertThat(task, instanceOf(Compile))
        assertThat(task.dependsOn, hasItem(JavaPlugin.PROCESS_RESOURCES_TASK_NAME))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME)
        assertThat(task, instanceOf(Copy))
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.COMPILE_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.COMPILE_TESTS_TASK_NAME)
        assertThat(task, instanceOf(Compile))
        assertThat(task.dependsOn, hasItem(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.TEST_TASK_NAME)
        assertThat(task, instanceOf(org.gradle.api.tasks.testing.Test))
        assertThat(task.dependsOn, hasItem(JavaPlugin.COMPILE_TESTS_TASK_NAME))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.LIBS_TASK_NAME)
        assertThat(task, instanceOf(Bundle))
        assertThat(task.dependsOn, hasItem(JavaPlugin.TEST_TASK_NAME))
        assertThat(task.defaultArchiveTypes, equalTo(project.archiveTypes))

        task = project.task(JavaPlugin.DISTS_TASK_NAME)
        assertThat(task, instanceOf(Bundle))
        assertThat(task.dependsOn, hasItem(JavaPlugin.LIBS_TASK_NAME))
        assertThat(task.defaultArchiveTypes, equalTo(project.archiveTypes))

        task = project.task(JavaPlugin.JAVADOC_TASK_NAME)
        assertThat(task, instanceOf(Javadoc))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))

        task = project.task("build" + Dependency.MASTER_CONFIGURATION[0].toUpperCase() + Dependency.MASTER_CONFIGURATION[1..-1])
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task.dependsOn.iterator().next().getDependencies(null),
                equalTo([project.task("archive_jar")] as Set))
    }

    @Test public void appliesMappingsToTasksCreatedByBuildScript() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.createTask('customResources', type: Copy)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.INIT_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customCompile', type: Compile)
        assertThat(task.dependsOn, hasItem(JavaPlugin.PROCESS_RESOURCES_TASK_NAME))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertThat(task.dependsOn, hasItem(JavaPlugin.COMPILE_TESTS_TASK_NAME))
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.createTask('customJar', type: Jar)
        assertThat(task.destinationDir, equalTo(project.buildDir))
        assertThat(task.baseDir, equalTo(project.classesDir))

        task = project.createTask('customWar', type: War)
        assertThat(task.destinationDir, equalTo(project.buildDir))
        assertThat(task.libExcludeConfigurations, equalTo([WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]))

        task = project.createTask('customZip', type: Zip)
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))

        task = project.createTask('customTar', type: Tar)
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))
    }

    @Test public void appliesBaseReportingPlugin() {
        javaPlugin.apply(project, new PluginRegistry())

        assertTrue(project.appliedPlugins.contains(ReportingBasePlugin))
    }
}
