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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.*
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
        javaPlugin.use(project, project.getPlugins())

        assertTrue(project.getPlugins().hasPlugin(ReportingBasePlugin))
        assertTrue(project.getPlugins().hasPlugin(BasePlugin))

        assertThat(project.convention.plugins.java, instanceOf(JavaPluginConvention))
    }

    @Test public void addsConfigurationsToTheProject() {
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
    }

    @Test public void createsStandardSourceSetsAndAppliesMappings() {
        javaPlugin.use(project, project.getPlugins())

        def set = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/main/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/main/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.compile))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/main')))
        assertThat(set.classes, builtBy(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.runtime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/main')))

        set = project.sourceSets[SourceSet.TEST_SOURCE_SET_NAME]
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/test/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/test/resources'))))
        assertThat(set.compileClasspath.sourceCollections, hasItem(project.configurations.testCompile))
        assertThat(set.compileClasspath, hasItem(new File(project.buildDir, 'classes/main')))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/test')))
        assertThat(set.classes, builtBy(JavaPlugin.TEST_CLASSES_TASK_NAME))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.testRuntime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/main')))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/test')))
    }

    @Test public void createsTasksAndAppliesMappingsForNewSourceSet() {
        javaPlugin.use(project, project.getPlugins())

        project.sourceSets.add('custom')
        def set = project.sourceSets.custom
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/custom/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/custom/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.compile))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
        assertThat(set.classes, builtBy('customClasses'))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.runtime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/custom')))

        def task = project.tasks['processCustomResources']
        assertThat(task.description, equalTo('Processes the custom resources.'))
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))
        assertThat(task.srcDirs, equalTo(project.sourceSets.custom.resources))

        task = project.tasks['compileCustomJava']
        assertThat(task.description, equalTo('Compiles the custom Java source.'))
        assertThat(task, instanceOf(Compile))
        assertThat(task, dependsOn())
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.java))
        assertThat(task.classpath, sameInstance(project.sourceSets.custom.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))

        task = project.tasks['customClasses']
        assertThat(task.description, equalTo('Assembles the custom classes.'))
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn('processCustomResources', 'compileCustomJava'))
    }
    
    @Test public void createsStandardTasksAndAppliesMappings() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.tasks[JavaPlugin.PROCESS_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.srcDirs, equalTo(project.sourceSets.main.resources))
        assertThat(task.destinationDir, equalTo(project.sourceSets.main.classesDir))

        task = project.tasks[JavaPlugin.COMPILE_JAVA_TASK_NAME]
        assertThat(task, instanceOf(Compile))
        assertThat(task, dependsOn())
        assertThat(task.defaultSource, equalTo(project.sourceSets.main.java))
        assertThat(task.classpath, sameInstance(project.sourceSets.main.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.main.classesDir))

        task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks[JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.srcDirs, equalTo(project.sourceSets.test.resources))
        assertThat(task.destinationDir, equalTo(project.sourceSets.test.classesDir))

        task = project.tasks[JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME]
        assertThat(task, instanceOf(Compile))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.defaultSource, equalTo(project.sourceSets.test.java))
        assertThat(task.classpath, sameInstance(project.sourceSets.test.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.test.classesDir))

        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME))

        task = project.tasks[JavaPlugin.TEST_TASK_NAME]
        assertThat(task, instanceOf(org.gradle.api.tasks.testing.Test))
        assertThat(task, dependsOn(JavaPlugin.TEST_CLASSES_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.configuration, equalTo(project.sourceSets.test.runtimeClasspath))
        assertThat(task.testClassesDir, equalTo(project.sourceSets.test.classesDir))

        task = project.tasks[JavaPlugin.JAR_TASK_NAME]
        assertThat(task, instanceOf(Jar))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.sourceSets.main.classesDir))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaPlugin.CHECK_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.TEST_TASK_NAME))

        task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]
        assertThat(task, instanceOf(Javadoc))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.defaultSource, sameInstance(project.sourceSets.main.allJava))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.classes))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/javadoc")))
        assertThat(task.title, equalTo(project.apiDocTitle))
        assertThat(task.optionsFile, equalTo(project.file('build/tmp/javadoc.options')))

        task = project.tasks["buildArchives"]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaPlugin.BUILD_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(BasePlugin.ASSEMBLE_TASK_NAME, JavaPlugin.CHECK_TASK_NAME))

                task = project.tasks[JavaPlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.BUILD_TASK_NAME))

        task = project.tasks[JavaPlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.BUILD_TASK_NAME))
    }

    @Test public void appliesMappingsToTasksDefinedByBuildScript() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.createTask('customCompile', type: Compile)
        assertThat(task.classpath, sameInstance(project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.sourceCompatibility, equalTo(project.sourceCompatibility.toString()))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertThat(task, dependsOn(JavaPlugin.TEST_CLASSES_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.configuration, equalTo(project.sourceSets.test.runtimeClasspath))
        assertThat(task.testClassesDir, equalTo(project.sourceSets.test.classesDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.classes))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.compileClasspath))
        assertThat(task.defaultSource, sameInstance(project.sourceSets.main.allJava))
        assertThat(task.destinationDir, equalTo((project.file("$project.docsDir/javadoc"))))
        assertThat(task.optionsFile, equalTo(project.file('build/tmp/javadoc.options')))
        assertThat(task.title, equalTo(project.apiDocTitle))
    }

    @Test public void appliesMappingsToCustomJarTasks() {
        javaPlugin.use(project, project.getPlugins())

        def task = project.createTask('customJar', type: Jar)
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.baseDir, equalTo(project.sourceSets.main.classesDir))
    }

    @Test public void buildOtherProjects() {
        DefaultProject commonProject = HelperUtil.createChildProject(project, "common", HelperUtil.makeNewTestDir("common"));
        DefaultProject middleProject = HelperUtil.createChildProject(project, "middle", HelperUtil.makeNewTestDir("middle"));
        DefaultProject appProject = HelperUtil.createChildProject(project, "app", HelperUtil.makeNewTestDir("app"));

        javaPlugin.use(project, project.getPlugins());
        javaPlugin.use(commonProject, commonProject.getPlugins());
        javaPlugin.use(middleProject, middleProject.getPlugins());
        javaPlugin.use(appProject, appProject.getPlugins());

        appProject.dependencies {
            compile project(path: middleProject.path, configuration: 'compile')
        }
        middleProject.dependencies {
            compile project(path: commonProject.path, configuration: 'compile')
        }

        Task task = middleProject.tasks[JavaPlugin.BUILD_NEEDED_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build',':common:build'] as Set))

        task = middleProject.tasks[JavaPlugin.BUILD_DEPENDENTS_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build',':app:build'] as Set))
    }
}
