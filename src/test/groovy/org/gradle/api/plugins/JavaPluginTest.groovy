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
import org.gradle.api.tasks.Resources
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * @author Hans Dockter
 */
// todo Make test stronger
// This is a very weak test. But due to the dynamic nature of Groovy, it does help to find bugs.
class JavaPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaPlugin javaPlugin = new JavaPlugin()

    @Test public void testApplyCreatesConfigurations() {
        javaPlugin.apply(project, new PluginRegistry())

        def configuration = project.configurations.get(JavaPlugin.COMPILE)
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.get(JavaPlugin.RUNTIME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(JavaPlugin.TEST_COMPILE)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.get(JavaPlugin.TEST_RUNTIME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.TEST_COMPILE, JavaPlugin.RUNTIME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(Dependency.DEFAULT_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(Dependency.MASTER_CONFIGURATION, JavaPlugin.RUNTIME)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(Dependency.MASTER_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.get(JavaPlugin.DISTS)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void createsTasksAndAppliesMappings() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.task(JavaPlugin.RESOURCES)
        assertThat(task, instanceOf(Resources))
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.INIT)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.COMPILE)
        assertThat(task, instanceOf(Compile))
        assertThat(task.dependsOn, hasItem(JavaPlugin.RESOURCES))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.COMPILE)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.task(JavaPlugin.TEST_RESOURCES)
        assertThat(task, instanceOf(Resources))
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.COMPILE)))
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.TEST_COMPILE)
        assertThat(task, instanceOf(Compile))
        assertThat(task.dependsOn, hasItem(JavaPlugin.TEST_RESOURCES))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.TEST_COMPILE)))
        assertThat(task.destinationDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.TEST)
        assertThat(task, instanceOf(org.gradle.api.tasks.testing.Test))
        assertThat(task.dependsOn, hasItem(JavaPlugin.TEST_COMPILE))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.TEST_RUNTIME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.task(JavaPlugin.LIBS)
        assertThat(task, instanceOf(Bundle))
        assertThat(task.dependsOn, hasItem(JavaPlugin.TEST))
        assertThat(task.defaultArchiveTypes, equalTo(project.archiveTypes))

        task = project.task(JavaPlugin.DISTS)
        assertThat(task, instanceOf(Bundle))
        assertThat(task.dependsOn, hasItem(JavaPlugin.LIBS))
        assertThat(task.defaultArchiveTypes, equalTo(project.archiveTypes))

        task = project.task(JavaPlugin.JAVADOC)
        assertThat(task, instanceOf(Javadoc))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.COMPILE)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))
    }

    @Test public void appliesMappingsToTasksCreatedByBuildScript() {
        javaPlugin.apply(project, new PluginRegistry())

        def task = project.createTask('customResources', type: Resources)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.INIT)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customCompile', type: Compile)
        assertThat(task.dependsOn, hasItem(JavaPlugin.RESOURCES))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.COMPILE)))
        assertThat(task.destinationDir, equalTo(project.classesDir))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertThat(task.dependsOn, hasItem(JavaPlugin.TEST_COMPILE))
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.TEST_RUNTIME)))
        assertThat(task.testClassesDir, equalTo(project.testClassesDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertThat(task.configuration, equalTo(project.configurations.get(JavaPlugin.COMPILE)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))
    }

    @Test public void appliesBaseReportingPlugin() {
        javaPlugin.apply(project, new PluginRegistry())

        assertTrue(project.appliedPlugins.contains(ReportingBasePlugin))
    }
}
