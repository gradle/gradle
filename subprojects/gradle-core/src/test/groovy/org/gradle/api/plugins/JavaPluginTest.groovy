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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

/**
 * @author Hans Dockter
 */
class JavaPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaPlugin javaPlugin = new JavaPlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaPlugin.use(project, project.getPlugins())

        assertTrue(project.getPlugins().hasPlugin(ReportingBasePlugin))
        assertTrue(project.getPlugins().hasPlugin(BasePlugin))

        assertThat(project.convention.plugins.java, instanceOf(JavaPluginConvention))
    }

    @Test public void createsConfigurations() {
        javaPlugin.use(project, project.getPlugins())

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
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(Dependency.ARCHIVES_CONFIGURATION, JavaPlugin.RUNTIME_CONFIGURATION_NAME)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.DISTS_TASK_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void createsSourceSetsAndAppliesMappings() {
        javaPlugin.use(project, project.getPlugins())

        def set = project.source[JavaPlugin.MAIN_SOURCE_SET_NAME]
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/main')))

        set = project.source[JavaPlugin.TEST_SOURCE_SET_NAME]
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/test')))
    }

    @Test public void appliesMappingsToNewSourceSets() {
        javaPlugin.use(project, project.getPlugins())

        project.source.add('custom')
        def set = project.source.custom
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
    }
    
    @Test public void createsTasksAndAppliesMappings() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.tasks[JavaPlugin.PROCESS_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertDependsOn(task)
        assertThat(task.destinationDir, equalTo(project.source.main.classesDir))

        task = project.tasks[JavaPlugin.COMPILE_TASK_NAME]
        assertThat(task, instanceOf(Compile))
        assertDependsOn(task)
        assertThat(task.classpath, sameInstance(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.source.main.classesDir))

        task = project.tasks[JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertDependsOn(task)
        assertThat(task.destinationDir, equalTo(project.source.test.classesDir))

        task = project.tasks[JavaPlugin.COMPILE_TESTS_TASK_NAME]
        assertThat(task, instanceOf(Compile))
        assertDependsOn(task, JavaPlugin.COMPILE_TASK_NAME)
        assertThat(task.classpath, sameInstance(project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.source.test.classesDir))

        task = project.tasks[JavaPlugin.TEST_TASK_NAME]
        assertThat(task, instanceOf(org.gradle.api.tasks.testing.Test))
        assertDependsOn(task, JavaPlugin.COMPILE_TESTS_TASK_NAME,
                              JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME,
                              JavaPlugin.COMPILE_TASK_NAME,
                              JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.source.test.classesDir))

        task = project.tasks[JavaPlugin.JAR_TASK_NAME]
        assertThat(task, instanceOf(Jar))
        assertDependsOn(task, JavaPlugin.COMPILE_TASK_NAME,
                              JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.source.main.classesDir))

        task = project.tasks[JavaPlugin.LIBS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.JAR_TASK_NAME)

        task = project.tasks[JavaPlugin.DISTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.LIBS_TASK_NAME)

        task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]
        assertThat(task, instanceOf(Javadoc))
        assertDependsOn(task)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))

        task = project.tasks["buildArchives"]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.JAR_TASK_NAME)

        task = project.tasks[JavaPlugin.BUILD_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.DISTS_TASK_NAME,
                              JavaPlugin.TEST_TASK_NAME)

        task = project.tasks[JavaPlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.BUILD_TASK_NAME)

        task = project.tasks[JavaPlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, JavaPlugin.BUILD_TASK_NAME)
    }

    @Test public void appliesMappingsToTasksDefinedByBuildScript() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.createTask('customCompile', type: Compile)
        assertThat(task.classpath, sameInstance(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.source.main.classesDir))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertDependsOn(task, JavaPlugin.COMPILE_TESTS_TASK_NAME,
                              JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME,
                              JavaPlugin.COMPILE_TASK_NAME,
                              JavaPlugin.PROCESS_RESOURCES_TASK_NAME)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)))
        assertThat(task.testClassesDir, equalTo(project.source.test.classesDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertDependsOn(task)
        assertThat(task.configuration, equalTo(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.destinationDir, equalTo(project.javadocDir))
    }

    @Test public void appliesMappingsToArchiveTasks() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.createTask('customJar', type: Jar)
        assertDependsOn(task, JavaPlugin.PROCESS_RESOURCES_TASK_NAME,
                              JavaPlugin.COMPILE_TASK_NAME)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.source.main.classesDir))

        assertDependsOn(project.tasks[JavaPlugin.LIBS_TASK_NAME], JavaPlugin.JAR_TASK_NAME, 'customJar')

        task = project.createTask('customZip', type: Zip)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.LIBS_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))

        assertDependsOn(project.tasks[JavaPlugin.DISTS_TASK_NAME], JavaPlugin.LIBS_TASK_NAME, 'customZip')

        task = project.createTask('customTar', type: Tar)
        assertThat(task.dependsOn, equalTo(toSet(JavaPlugin.LIBS_TASK_NAME)))
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))

        assertDependsOn(project.tasks[JavaPlugin.DISTS_TASK_NAME], JavaPlugin.LIBS_TASK_NAME, 'customZip', 'customTar')
    }

    @Test public void buildOtherProjects() {
        DefaultProject commonProject = HelperUtil.createChildProject(project, "common", HelperUtil.makeNewTestDir("common"));
        DefaultProject middleProject = HelperUtil.createChildProject(project, "middle", HelperUtil.makeNewTestDir("middle"));
        DefaultProject appProject = HelperUtil.createChildProject(project, "app", HelperUtil.makeNewTestDir("app"));

        javaPlugin.use(project, project.getPlugins());
        javaPlugin.use(commonProject, commonProject.getPlugins());
        javaPlugin.use(middleProject, middleProject.getPlugins());
        javaPlugin.use(appProject, appProject.getPlugins());

        appProject.getConfigurations().findByName("compile").addDependency(new DefaultProjectDependency(middleProject, "compile"));
        middleProject.getConfigurations().findByName("compile").addDependency(new DefaultProjectDependency(commonProject, "compile"));

        Task task = middleProject.tasks[JavaPlugin.BUILD_NEEDED_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build',':common:build'] as Set))

        task = middleProject.tasks[JavaPlugin.BUILD_DEPENDENTS_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build',':app:build'] as Set))
    }

    private void assertDependsOn(Task task, String... names) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, equalTo(toSet(names)))
    }
}
