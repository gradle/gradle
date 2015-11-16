/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.api.internal.java.JavaLibrary
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.Test

import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class JavaPluginTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private final def project = TestUtil.createRootProject()
    private final def javaPlugin = new JavaPlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaPlugin.apply(project)

        def component = project.services.get(ComponentRegistry).mainComponent
        assertThat(component, instanceOf(BuildableJavaComponent))
        assertThat(component.rebuildTasks, equalTo([BasePlugin.CLEAN_TASK_NAME, JavaBasePlugin.BUILD_TASK_NAME]))
        assertThat(component.buildTasks, equalTo([JavaBasePlugin.BUILD_TASK_NAME]))
        assertThat(component.runtimeClasspath, notNullValue())
        assertThat(component.compileDependencies, equalTo(project.configurations.compile))
    }

    @Test public void addsConfigurationsToTheProject() {
        javaPlugin.apply(project)

        def compile = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(compile.extendsFrom, equalTo(toSet()))
        assertFalse(compile.visible)
        assertTrue(compile.transitive)

        def runtime = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(runtime.extendsFrom, equalTo(toSet(compile)))
        assertFalse(runtime.visible)
        assertTrue(runtime.transitive)

        def testCompile = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)
        assertThat(testCompile.extendsFrom, equalTo(toSet(compile)))
        assertFalse(testCompile.visible)
        assertTrue(testCompile.transitive)

        def testRuntime = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
        assertThat(testRuntime.extendsFrom, equalTo(toSet(runtime, testCompile)))
        assertFalse(testRuntime.visible)
        assertTrue(testRuntime.transitive)

        def defaultConfig = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
        assertThat(defaultConfig.extendsFrom, equalTo(toSet(runtime)))
    }

    @Test public void addsJarAsPublication() {
        javaPlugin.apply(project)

        def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(runtimeConfiguration.artifacts.collect { it.archiveTask }, equalTo([project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)]))

        def archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        assertThat(archivesConfiguration.artifacts.collect { it.archiveTask }, equalTo([project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)]))
    }

    @Test public void addsJavaLibraryComponent() {
        javaPlugin.apply(project)

        def jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)

        JavaLibrary javaLibrary = project.components.getByName("java")
        assertThat(javaLibrary.artifacts.collect {it.archiveTask}, equalTo([jarTask]))
        assertThat(javaLibrary.runtimeDependencies, equalTo(project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).allDependencies))
    }

    @Test public void createsStandardSourceSetsAndAppliesMappings() {
        javaPlugin.apply(project)

        def set = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/main/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/main/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.compile))
        assertThat(set.output.classesDir, equalTo(new File(project.buildDir, 'classes/main')))
        assertThat(set.output.resourcesDir, equalTo(new File(project.buildDir, 'resources/main')))
        assertThat(set.output, TaskDependencyMatchers.builtBy(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.runtime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/main')))

        set = project.sourceSets[SourceSet.TEST_SOURCE_SET_NAME]
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/test/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/test/resources'))))
        assertThat(set.compileClasspath.sourceCollections, hasItem(project.configurations.testCompile))
        assertThat(set.compileClasspath, hasItem(new File(project.buildDir, 'classes/main')))
        assertThat(set.output.classesDir, equalTo(new File(project.buildDir, 'classes/test')))
        assertThat(set.output.resourcesDir, equalTo(new File(project.buildDir, 'resources/test')))
        assertThat(set.output, TaskDependencyMatchers.builtBy(JavaPlugin.TEST_CLASSES_TASK_NAME))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.testRuntime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/main')))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/test')))
    }

    @Test public void createsMappingsForCustomSourceSets() {
        javaPlugin.apply(project)

        def set = project.sourceSets.create('custom')
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/custom/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/custom/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.customCompile))
        assertThat(set.output.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
        assertThat(set.output, TaskDependencyMatchers.builtBy('customClasses'))
        assertThat(set.runtimeClasspath, FileCollectionMatchers.sameCollection(set.output + project.configurations.customRuntime))
    }

    @Test public void createsStandardTasksAndAppliesMappings() {
        javaPlugin.apply(project)
        new TestFile(project.file("src/main/java/File.java")) << "foo"
        new TestFile(project.file("src/main/resources/thing.txt")) << "foo"
        new TestFile(project.file("src/test/java/File.java")) << "foo"
        new TestFile(project.file("src/test/resources/thing.txt")) << "foo"

        def task = project.tasks[JavaPlugin.PROCESS_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertThat(task, TaskDependencyMatchers.dependsOn())
        assertEquals(task.source.files, project.sourceSets.main.resources.files)
        assertThat(task.destinationDir, equalTo(project.sourceSets.main.output.resourcesDir))

        task = project.tasks[JavaPlugin.COMPILE_JAVA_TASK_NAME]
        assertThat(task, instanceOf(JavaCompile))
        assertThat(task, TaskDependencyMatchers.dependsOn())
        assertThat(task.classpath, sameInstance(project.sourceSets.main.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.main.output.classesDir))
        assertEquals(task.source.files, project.sourceSets.main.java.files)

        task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks[JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertThat(task, TaskDependencyMatchers.dependsOn())
        assertEquals(task.source.files, project.sourceSets.test.resources.files)
        assertThat(task.destinationDir, equalTo(project.sourceSets.test.output.resourcesDir))

        task = project.tasks[JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME]
        assertThat(task, instanceOf(JavaCompile))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.classpath, sameInstance(project.sourceSets.test.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.test.output.classesDir))
        assertEquals(task.source.files, project.sourceSets.test.java.files)

        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME))

        task = project.tasks[JavaPlugin.JAR_TASK_NAME]
        assertThat(task, instanceOf(Jar))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.mainSpec.sourcePaths, equalTo([project.sourceSets.main.output] as Set))
        assertThat(task.manifest, notNullValue())
        assertThat(task.manifest.mergeSpecs.size(), equalTo(0))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.TEST_TASK_NAME))

        project.sourceSets.main.java.srcDirs(tmpDir.getTestDirectory())
        tmpDir.file("SomeFile.java").touch()
        task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]
        assertThat(task, instanceOf(Javadoc))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.source.files, equalTo(project.sourceSets.main.allJava.files))
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/javadoc")))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))

        task = project.tasks["buildArchives"]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(BasePlugin.ASSEMBLE_TASK_NAME, JavaBasePlugin.CHECK_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME))
    }

    @Test void "configures test task"() {
        javaPlugin.apply(project)

        //when
        def task = project.tasks[JavaPlugin.TEST_TASK_NAME]

        //then
        assert task instanceof org.gradle.api.tasks.testing.Test
        assertThat(task, TaskDependencyMatchers.dependsOn(JavaPlugin.TEST_CLASSES_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
        assert task.classpath == project.sourceSets.test.runtimeClasspath
        assert task.testClassesDir == project.sourceSets.test.output.classesDir
        assert task.workingDir == project.projectDir
    }

    @Test public void appliesMappingsToTasksAddedByTheBuildScript() {
        javaPlugin.apply(project);

        def task = project.task('customTest', type: org.gradle.api.tasks.testing.Test.class)
        assertThat(task.classpath, equalTo(project.sourceSets.test.runtimeClasspath))
        assertThat(task.testClassesDir, equalTo(project.sourceSets.test.output.classesDir))
        assertThat(task.workingDir, equalTo(project.projectDir))
        assertThat(task.reports.junitXml.destination, equalTo(project.testResultsDir))
        assertThat(task.reports.html.destination, equalTo(project.testReportDir))
    }

    @Test public void buildOtherProjects() {
        DefaultProject commonProject = TestUtil.createChildProject(project, "common");
        DefaultProject middleProject = TestUtil.createChildProject(project, "middle");
        DefaultProject appProject = TestUtil.createChildProject(project, "app");

        javaPlugin.apply(project);
        javaPlugin.apply(commonProject);
        javaPlugin.apply(middleProject);
        javaPlugin.apply(appProject);

        appProject.dependencies {
            compile middleProject
        }
        middleProject.dependencies {
            compile commonProject
        }

        Task task = middleProject.tasks['buildNeeded'];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':common:buildNeeded'] as Set))

        task = middleProject.tasks['buildDependents'];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':app:buildDependents'] as Set))
    }
}
