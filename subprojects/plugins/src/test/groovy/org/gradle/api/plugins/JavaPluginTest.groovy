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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.plugins.EmbeddableJavaProject
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.junit.Test
import static org.gradle.util.Matchers.builtBy
import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class JavaPluginTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder()
    private final Project project = HelperUtil.createRootProject()
    private final JavaPlugin javaPlugin = new JavaPlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaPlugin.apply(project)

        assertThat(project.convention.plugins.embeddedJavaProject, instanceOf(EmbeddableJavaProject))
        assertThat(project.convention.plugins.embeddedJavaProject.rebuildTasks, equalTo([BasePlugin.CLEAN_TASK_NAME, JavaBasePlugin.BUILD_TASK_NAME]))
        assertThat(project.convention.plugins.embeddedJavaProject.buildTasks, equalTo([JavaBasePlugin.BUILD_TASK_NAME]))
        assertThat(project.convention.plugins.embeddedJavaProject.runtimeClasspath, sameInstance(project.sourceSets.main.runtimeClasspath))
    }

    @Test public void addsConfigurationsToTheProject() {
        javaPlugin.apply(project)

        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(Dependency.ARCHIVES_CONFIGURATION, JavaPlugin.RUNTIME_CONFIGURATION_NAME)))
    }

    @Test public void createsStandardSourceSetsAndAppliesMappings() {
        javaPlugin.apply(project)

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

    @Test public void createsMappingsForCustomSourceSets() {
        javaPlugin.apply(project)

        def set = project.sourceSets.add('custom')
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/custom/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/custom/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.compile))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
        assertThat(set.classes, builtBy('customClasses'))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.runtime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/custom')))
    }

    @Test public void createsStandardTasksAndAppliesMappings() {
        javaPlugin.apply(project)

        def task = project.tasks[JavaPlugin.PROCESS_RESOURCES_TASK_NAME]
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.defaultSource, equalTo(project.sourceSets.main.resources))
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
        assertThat(task.defaultSource, equalTo(project.sourceSets.test.resources))
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
        assertThat(task.classpath, equalTo(project.sourceSets.test.runtimeClasspath))
        assertThat(task.testClassesDir, equalTo(project.sourceSets.test.classesDir))
        assertThat(task.workingDir, equalTo(project.projectDir))

        task = project.tasks[JavaPlugin.JAR_TASK_NAME]
        assertThat(task, instanceOf(Jar))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.copyAction.mainSpec.sourcePaths, equalTo([project.sourceSets.main.classes] as Set))
        assertThat(task.manifest, notNullValue())
        assertThat(task.manifest, not(sameInstance(project.manifest)))
        assertThat(task.manifest.mergeSpecs.size(), equalTo(1))
        assertThat(task.manifest.mergeSpecs[0].mergePaths[0], sameInstance(project.manifest))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.TEST_TASK_NAME))

        project.sourceSets.main.java.srcDirs(tmpDir.getDir())
        tmpDir.file("SomeFile.java").touch()
        task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]
        assertThat(task, instanceOf(Javadoc))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.source.files, equalTo(project.sourceSets.main.allJava.files))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.classes))
        assertThat(task.classpath.sourceCollections, hasItem(project.sourceSets.main.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/javadoc")))
        assertThat(task.title, equalTo(project.apiDocTitle))

        task = project.tasks["buildArchives"]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaPlugin.JAR_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(BasePlugin.ASSEMBLE_TASK_NAME, JavaBasePlugin.CHECK_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))
    }

    @Test public void appliesMappingsToTasksAddedByTheBuildScript() {
        javaPlugin.apply(project);

        def task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test.class)
        assertThat(task.classpath, equalTo(project.sourceSets.test.runtimeClasspath))
        assertThat(task.testClassesDir, equalTo(project.sourceSets.test.classesDir))
        assertThat(task.workingDir, equalTo(project.projectDir))
        assertThat(task.testResultsDir, equalTo(project.testResultsDir))
        assertThat(task.testReportDir, equalTo(project.testReportDir))
    }

    @Test public void buildOtherProjects() {
        DefaultProject commonProject = HelperUtil.createChildProject(project, "common");
        DefaultProject middleProject = HelperUtil.createChildProject(project, "middle");
        DefaultProject appProject = HelperUtil.createChildProject(project, "app");

        javaPlugin.apply(project);
        javaPlugin.apply(commonProject);
        javaPlugin.apply(middleProject);
        javaPlugin.apply(appProject);

        appProject.dependencies {
            compile project(path: middleProject.path, configuration: 'compile')
        }
        middleProject.dependencies {
            compile project(path: commonProject.path, configuration: 'compile')
        }

        Task task = middleProject.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':common:build'] as Set))

        task = middleProject.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':app:build'] as Set))
    }
}
