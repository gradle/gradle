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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TestUtil
import org.junit.Assert

import static org.gradle.api.file.FileCollectionMatchers.sameCollection
import static org.gradle.api.tasks.TaskDependencyMatchers.builtBy
import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet

class JavaPluginTest extends AbstractProjectBuilderSpec {
    private final def javaPlugin = new JavaPlugin()

    def appliesBasePluginsAndAddsConventionObject() {
        given:
        javaPlugin.apply(project)

        when:
        def component = project.services.get(ComponentRegistry).mainComponent

        then:
        component instanceof BuildableJavaComponent
        component.rebuildTasks == [BasePlugin.CLEAN_TASK_NAME, JavaBasePlugin.BUILD_TASK_NAME]
        component.buildTasks == [JavaBasePlugin.BUILD_TASK_NAME]
        component.runtimeClasspath != null
        component.compileDependencies == project.configurations.compile
    }

    def addsConfigurationsToTheProject() {
        given:
        javaPlugin.apply(project)

        when:
        def compile = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)

        then:
        compile.extendsFrom == toSet()
        !compile.visible
        compile.transitive

        when:
        def implementation = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        implementation.extendsFrom == toSet(compile)
        !implementation.visible
        !implementation.canBeConsumed
        !implementation.canBeResolved

        when:
        def runtime = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtime.extendsFrom == toSet(compile)
        !runtime.visible
        runtime.transitive

        when:
        def runtimeOnly = project.configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeOnly.transitive
        !runtimeOnly.visible
        !runtimeOnly.canBeConsumed
        !runtimeOnly.canBeResolved
        runtimeOnly.extendsFrom == [] as Set

        when:
        def runtimeElements = project.configurations.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        runtimeElements.transitive
        !runtimeElements.visible
        runtimeElements.canBeConsumed
        !runtimeElements.canBeResolved
        runtimeElements.extendsFrom == [implementation] as Set

        when:
        def runtimeClasspath = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        then:
        runtimeClasspath.transitive
        !runtimeClasspath.visible
        !runtimeClasspath.canBeConsumed
        runtimeClasspath.canBeResolved
        runtimeClasspath.extendsFrom == [runtimeOnly, runtime, runtimeElements] as Set

        when:
        def compileOnly = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        compileOnly.extendsFrom == toSet(implementation)
        !compileOnly.visible
        compileOnly.transitive

        when:
        def compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        compileClasspath.extendsFrom == toSet(compileOnly)
        !compileClasspath.visible
        compileClasspath.transitive

        when:
        def testCompile = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)

        then:
        testCompile.extendsFrom == toSet(implementation)
        !testCompile.visible
        testCompile.transitive

        when:
        def testImplementation = project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        testImplementation.extendsFrom == toSet(testCompile, implementation)
        !testImplementation.visible
        testImplementation.transitive

        when:
        def testRuntime = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)

        then:
        testRuntime.extendsFrom == toSet(runtime, testCompile, testImplementation)
        !testRuntime.visible
        testRuntime.transitive

        when:
        def testRuntimeOnly = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        testRuntimeOnly.transitive
        !testRuntimeOnly.visible
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testRuntimeOnly.extendsFrom == [] as Set

        when:
        def testCompileOnly = project.configurations.getByName(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        testCompileOnly.extendsFrom == toSet(testImplementation)
        !testCompileOnly.visible
        testCompileOnly.transitive

        when:
        def testCompileClasspath = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        testCompileClasspath.extendsFrom == toSet(testCompileOnly)
        !testCompileClasspath.visible
        testCompileClasspath.transitive

        when:
        def testRuntimeClasspath = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        then:
        testRuntimeClasspath.extendsFrom == toSet(testRuntime, testRuntimeOnly, testImplementation)
        !testRuntimeClasspath.visible
        testRuntimeClasspath.transitive
        !testRuntimeClasspath.canBeConsumed
        testRuntimeClasspath.canBeResolved

        when:
        def defaultConfig = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)

        then:
        defaultConfig.extendsFrom == toSet(runtime)
    }

    def addsJarAsPublication() {
        given:
        javaPlugin.apply(project)

        when:
        def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtimeConfiguration.artifacts.collect { it.archiveTask } == [project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)]

        when:
        def archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)

        then:
        archivesConfiguration.artifacts.collect { it.archiveTask } == [project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)]
    }

    def addsJavaLibraryComponent() {
        given:
        javaPlugin.apply(project)

        when:
        def jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)
        def javaLibrary = project.components.getByName("java")

        then:
        javaLibrary.artifacts.collect {it.archiveTask} == [jarTask]
        javaLibrary.runtimeDependencies == project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).allDependencies
    }

    def createsStandardSourceSetsAndAppliesMappings() {
        given:
        javaPlugin.apply(project)

        when:
        def set = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]

        then:
        set.java.srcDirs == toLinkedSet(project.file('src/main/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/main/resources'))
        set.compileClasspath.is(project.configurations.compileClasspath)
        set.output.classesDir == new File(project.buildDir, 'classes/main')
        set.output.resourcesDir == new File(project.buildDir, 'resources/main')
        set.getOutput() builtBy(JavaPlugin.CLASSES_TASK_NAME)
        set.runtimeClasspath.sourceCollections.contains(project.configurations.runtime)
        set.runtimeClasspath.contains(new File(project.buildDir, 'classes/main'))

        when:
        set = project.sourceSets[SourceSet.TEST_SOURCE_SET_NAME]

        then:
        set.java.srcDirs == toLinkedSet(project.file('src/test/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/test/resources'))
        set.compileClasspath.sourceCollections.contains(project.configurations.testCompileClasspath)
        set.compileClasspath.contains(new File(project.buildDir, 'classes/main'))
        set.output.classesDir == new File(project.buildDir, 'classes/test')
        set.output.resourcesDir == new File(project.buildDir, 'resources/test')
        set.getOutput() builtBy(JavaPlugin.TEST_CLASSES_TASK_NAME)
        set.runtimeClasspath.sourceCollections.contains(project.configurations.testRuntime)
        set.runtimeClasspath.contains(new File(project.buildDir, 'classes/main'))
        set.runtimeClasspath.contains(new File(project.buildDir, 'classes/test'))
    }

    def createsMappingsForCustomSourceSets() {
        given:
        javaPlugin.apply(project)

        when:
        SourceSet set = project.sourceSets.create('custom')

        then:
        set.java.srcDirs == toLinkedSet(project.file('src/custom/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/custom/resources'))
        set.compileClasspath.is(project.configurations.customCompileClasspath)
        set.output.classesDir == new File(project.buildDir, 'classes/custom')
        set.getOutput() builtBy('customClasses')
        Assert.assertThat(set.runtimeClasspath, sameCollection(set.output + project.configurations.customRuntime))
    }

    def createsStandardTasksAndAppliesMappings() {
        given:
        javaPlugin.apply(project)
        new TestFile(project.file("src/main/java/File.java")) << "foo"
        new TestFile(project.file("src/main/resources/thing.txt")) << "foo"
        new TestFile(project.file("src/test/java/File.java")) << "foo"
        new TestFile(project.file("src/test/resources/thing.txt")) << "foo"

        when:
        def task = project.tasks[JavaPlugin.PROCESS_RESOURCES_TASK_NAME]

        then:
        task instanceof Copy
        task dependsOn()
        task.source.files == project.sourceSets.main.resources.files
        task.destinationDir == project.sourceSets.main.output.resourcesDir

        when:
        task = project.tasks[JavaPlugin.COMPILE_JAVA_TASK_NAME]

        then:
        task instanceof JavaCompile
        task dependsOn()
        task.classpath.is(project.sourceSets.main.compileClasspath)
        task.destinationDir == project.sourceSets.main.output.classesDir
        task.source.files == project.sourceSets.main.java.files

        when:
        task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, JavaPlugin.COMPILE_JAVA_TASK_NAME)

        when:
        task = project.tasks[JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME]

        then:
        task instanceof Copy
        task dependsOn()
        task.source.files == project.sourceSets.test.resources.files
        task.destinationDir == project.sourceSets.test.output.resourcesDir

        when:
        task = project.tasks[JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME]

        then:
        task instanceof JavaCompile
        task dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        task.classpath.is(project.sourceSets.test.compileClasspath)
        task.destinationDir == project.sourceSets.test.output.classesDir
        task.source.files == project.sourceSets.test.java.files

        when:
        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME)

        when:
        task = project.tasks[JavaPlugin.JAR_TASK_NAME]

        then:
        task instanceof Jar
        task dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        task.destinationDir == project.libsDir
        task.mainSpec.sourcePaths == [project.sourceSets.main.output] as Set
        task.manifest != null
        task.manifest.mergeSpecs.size() == 0

        when:
        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]

        then:
        task dependsOn(JavaPlugin.JAR_TASK_NAME)

        when:
        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaPlugin.TEST_TASK_NAME)

        when:
        project.sourceSets.main.java.srcDirs(temporaryFolder.getTestDirectory())
        temporaryFolder.file("SomeFile.java").touch()
        task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]

        then:
        task instanceof Javadoc
        task dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        task.source.files == project.sourceSets.main.allJava.files
        Assert.assertThat(task.classpath, sameCollection(project.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        task.destinationDir == project.file("$project.docsDir/javadoc")
        task.title == project.extensions.getByType(ReportingExtension).apiDocTitle

        when:
        task = project.tasks["buildArchives"]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaPlugin.JAR_TASK_NAME)

        when:
        task = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(BasePlugin.ASSEMBLE_TASK_NAME, JavaBasePlugin.CHECK_TASK_NAME)

        when:
        task = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaBasePlugin.BUILD_TASK_NAME)

        when:
        task = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]

        then:
        task instanceof DefaultTask
        task dependsOn(JavaBasePlugin.BUILD_TASK_NAME)
    }

    def "configures test task"() {
        given:
        javaPlugin.apply(project)

        when:
        def task = project.tasks[JavaPlugin.TEST_TASK_NAME]

        then:
        task instanceof org.gradle.api.tasks.testing.Test
        task dependsOn(JavaPlugin.TEST_CLASSES_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME)
        task.classpath == project.sourceSets.test.runtimeClasspath
        task.testClassesDir == project.sourceSets.test.output.classesDir
        task.workingDir == project.projectDir
    }

    def appliesMappingsToTasksAddedByTheBuildScript() {
        given:
        javaPlugin.apply(project);

        when:
        def task = project.task('customTest', type: org.gradle.api.tasks.testing.Test.class)

        then:
        task.classpath == project.sourceSets.test.runtimeClasspath
        task.testClassesDir == project.sourceSets.test.output.classesDir
        task.workingDir == project.projectDir
        task.reports.junitXml.destination == new File(project.testResultsDir, 'customTest')
        task.reports.html.destination == new File(project.testReportDir, 'customTest')
    }

    def buildOtherProjects() {
        given:
        def commonProject = TestUtil.createChildProject(project, "common");
        def middleProject = TestUtil.createChildProject(project, "middle");
        def appProject = TestUtil.createChildProject(project, "app");

        when:
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

        and:
        def task = middleProject.tasks['buildNeeded'];

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [':middle:build', ':common:buildNeeded'] as Set

        when:
        task = middleProject.tasks['buildDependents'];

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [':middle:build', ':app:buildDependents'] as Set
    }
}
